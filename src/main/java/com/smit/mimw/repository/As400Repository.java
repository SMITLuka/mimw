package com.smit.mimw.repository;

import com.smit.mimw.dto.Brand;
import com.smit.mimw.dto.CompanyData;
import com.smit.mimw.dto.FormBuildRequest;
import com.smit.mimw.dto.MimvDetaljItem;
import com.smit.mimw.dto.MimvProcessedItem;
import com.smit.mimw.dto.TaxOffice;
import com.smit.mimw.tenant.As400DirectPools;
import com.smit.mimw.tenant.TenantConfigurationException;
import com.smit.mimw.tenant.TenantContext;
import com.smit.mimw.tenant.TenantSettings;
import com.smit.mimw.tenant.TenantSettingsProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repository for all database operations in the MI-MV module.
 *
 * Primary datasource: Pantheon SQL Server (MSSQL).
 * AS400 data is accessed via a SQL Server Linked Server.
 *
 * MIMV_ZAGLAVLJE and MIMV_DETALJ are local Pantheon MSSQL tables.
 * AS400 HF tables (hfs, hfk, hfb, etc.) are accessed via linked server.
 *
 * Every request works on the company in {@link TenantContext}: the JdbcTemplate's DataSource routes
 * to that company's SQL Server, and the AS400 linked server, catalog and libraries come from that
 * company's settings (Integration Hub or env variables).
 */
@Repository
public class As400Repository {

    private static final Logger log = LoggerFactory.getLogger(As400Repository.class);

    private static final String MIMV_ZAGLAVLJE = "MIMV_ZAGLAVLJE"; //$NON-NLS-1$
    private static final String MIMV_DETALJ = "MIMV_DETALJ"; //$NON-NLS-1$

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd"); //$NON-NLS-1$

    private final JdbcTemplate jdbcTemplate;

    private final TenantSettingsProvider settingsProvider;

    private final As400DirectPools as400DirectPools;

    /** Linked server names read from _cdp_param, per database (pool key), when the settings do not name one. */
    private final Map<String, String> linkedServerByDatabase = new ConcurrentHashMap<>();

    public As400Repository(JdbcTemplate jdbcTemplate, TenantSettingsProvider settingsProvider,
                           As400DirectPools as400DirectPools) {
        this.jdbcTemplate = jdbcTemplate;
        this.settingsProvider = settingsProvider;
        this.as400DirectPools = as400DirectPools;
    }

    /** true when the company has AS400_HOST: AS400 tables are read directly (JT400), not via the linked server. */
    private boolean direct() {
        return settings().directAs400();
    }

    /** JdbcTemplate for AS400 reads: direct AS400 connection, or the company's SQL Server (linked server). */
    private JdbcTemplate as400() {
        TenantSettings s = settings();
        return s.directAs400() ? as400DirectPools.template(s) : jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        if (settingsProvider.isHubEnabled()) {
            log.info("AS400 settings are resolved per company from Integration Hub"); //$NON-NLS-1$
        } else {
            TenantSettings env = settingsProvider.fromEnvironment();
            if (env.hfLibrary() == null) {
                log.warn("as400.data-group is not set. AS400 libraries cannot be computed — MIMV_DETALJ inserts will fail."); //$NON-NLS-1$
            } else {
                log.info("Computed libraries: main='{}', hf='{}', p='{}' (dataGroup='{}', branch='{}')", //$NON-NLS-1$
                        env.mainLibrary(), env.hfLibrary(), env.pLibrary(), env.dataGroup(), env.branch());
            }
        }
    }

    /** Settings of the company being served (environment settings outside a request in environment mode). */
    private TenantSettings settings() {
        TenantSettings current = TenantContext.get();
        if (current != null) {
            return current;
        }
        if (!settingsProvider.isHubEnabled()) {
            return settingsProvider.fromEnvironment();
        }
        throw new IllegalStateException("Nema mandatorId/companyId headera — ne znam za koju firmu se radi"); //$NON-NLS-1$
    }

    /**
     * Linked server name: from the company's settings (AS400_LINKED_SERVER), otherwise read once per
     * database from _cdp_param.aclinkserver and remembered.
     */
    private String linkedServer() {
        TenantSettings s = settings();
        if (s.linkedServer() != null && !s.linkedServer().isBlank()) {
            return s.linkedServer().trim();
        }
        String cached = linkedServerByDatabase.get(s.poolKey());
        if (cached != null) {
            return cached;
        }
        try {
            String ls = jdbcTemplate.queryForObject("SELECT TOP 1 aclinkserver FROM _cdp_param", String.class); //$NON-NLS-1$
            if (ls != null && !ls.isBlank()) {
                linkedServerByDatabase.put(s.poolKey(), ls.trim());
                log.info("Loaded linked server name from _cdp_param: '{}' ({})", ls.trim(), s.source()); //$NON-NLS-1$
                return ls.trim();
            }
            log.error("_cdp_param.aclinkserver is empty ({}). Set AS400_LINKED_SERVER.", s.source()); //$NON-NLS-1$
        } catch (Exception e) {
            log.error("Could not read linked server from _cdp_param ({}): {}. Set AS400_LINKED_SERVER.", s.source(), e.getMessage()); //$NON-NLS-1$
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // AS400 table reference helper
    // -------------------------------------------------------------------------

    /**
     * Builds the fully qualified four-part table name for AS400 via linked server.
     * Format: [linkedServer].[catalog].[library].[table]
     */
    /** Main data library of the request's company (AS400_DATA_GROUP + "D1", e.g. KB0D1). */
    private String mainLibrary() {
        String lib = settings().mainLibrary();
        if (lib == null) {
            throw new TenantConfigurationException("AS400_DATA_GROUP nije upisan (" + settings().source() //$NON-NLS-1$
                    + ") - ne znam iz koje AS400 knjiznice citati (npr. KB0 -> KB0D1)."); //$NON-NLS-1$
        }
        return lib;
    }

    private String as400Table(String library, String table) {
        if (direct()) {
            return library + "." + table; //$NON-NLS-1$
        }
        String linkedServer = linkedServer();
        if (linkedServer == null || linkedServer.isBlank()) {
            return library + "." + table; //$NON-NLS-1$
        }
        return "[" + linkedServer + "].[" + settings().catalog() + "].[" + library + "].[" + table + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    // -------------------------------------------------------------------------
    // Tax offices — AS400 IVASXT.KLCICPP
    // -------------------------------------------------------------------------

    /**
     * Fetches all tax/customs offices from AS400.
     */
    public List<TaxOffice> fetchTaxOffices() {
        String sql = "SELECT CIYTAA, CIL7AQ FROM " + as400Table("IVASXT", "KLCICPP"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        log.debug("fetchTaxOffices SQL: {}", sql); //$NON-NLS-1$
        return as400().query(sql, (rs, rowNum) ->
                TaxOffice.builder()
                        .code(trim(rs.getString("CIYTAA"))) //$NON-NLS-1$
                        .description(trim(rs.getString("CIL7AQ"))) //$NON-NLS-1$
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Vehicle brands — AS400 IVASXT.KLCHCPP
    // -------------------------------------------------------------------------

    /**
     * Fetches all vehicle brand codes and descriptions from AS400.
     */
    public List<Brand> fetchBrands() {
        String sql = "SELECT CHYSAA, CHL6AQ FROM " + as400Table("IVASXT", "KLCHCPP"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        log.debug("fetchBrands SQL: {}", sql); //$NON-NLS-1$
        return as400().query(sql, (rs, rowNum) ->
                Brand.builder()
                        .code(trim(rs.getString("CHYSAA"))) //$NON-NLS-1$
                        .description(trim(rs.getString("CHL6AQ"))) //$NON-NLS-1$
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // OIB — AS400 <data group>D1.HDB (e.g. KB0D1.HDB)
    // -------------------------------------------------------------------------

    /**
     * Fetches the taxpayer OIB from <data group>D1.HDB (e.g. KB0D1.HDB).
     * OIB occupies bytes 284-294 (length 11) inside the HDBINHALT packed field.
     * Uses SUBSTRING (T-SQL) since the query goes through the linked server.
     */
    public String fetchOib() {
        String sql = "SELECT TRIM(SUBSTRING(HDBINHALT, 284, 11)) AS OIB FROM " //$NON-NLS-1$
                + as400Table(mainLibrary(), "HDB") + " WHERE HDBSART = 'BEN'"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        log.debug("fetchOib SQL: {}", sql); //$NON-NLS-1$
        try {
            List<String> results = as400().query(sql, (rs, rowNum) -> rs.getString("OIB")); //$NON-NLS-1$
            if (results.isEmpty()) {
                log.warn("No BEN record found in HDB ({})", mainLibrary()); //$NON-NLS-1$
                return null;
            }
            String oib = results.get(0);
            return oib != null ? oib.trim() : null;
        } catch (Exception e) {
            log.warn("Could not fetch OIB from {}.HDB: {}", mainLibrary(), e.getMessage()); //$NON-NLS-1$
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Company data — AS400 <data group>D1.ZBEN1 (e.g. KB0D1.ZBEN1)
    // -------------------------------------------------------------------------

    /**
     * Fetches company name and address from <data group>D1.ZBEN1 (e.g. KB0D1.ZBEN1).
     * Returns empty strings on failure rather than propagating the exception,
     * so the form-build flow is not blocked when the table is temporarily unavailable.
     */
    public CompanyData fetchCompanyData() {
        String cols = "BENNAME1, BENNAME2, BENSTR, BENPLZ, BENORT"; //$NON-NLS-1$
        String sql = direct()
                ? "SELECT " + cols + " FROM " + as400Table(mainLibrary(), "ZBEN1") + " FETCH FIRST 1 ROWS ONLY" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                : "SELECT TOP 1 " + cols + " FROM " + as400Table(mainLibrary(), "ZBEN1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        log.debug("fetchCompanyData SQL: {}", sql); //$NON-NLS-1$
        try {
            return as400().queryForObject(sql, (rs, rowNum) -> {
                String name1 = trim(rs.getString("BENNAME1")); //$NON-NLS-1$
                String name2 = trim(rs.getString("BENNAME2")); //$NON-NLS-1$
                String str   = trim(rs.getString("BENSTR")); //$NON-NLS-1$
                String plz   = trim(rs.getString("BENPLZ")); //$NON-NLS-1$
                String ort   = trim(rs.getString("BENORT")); //$NON-NLS-1$
                String description = Stream.of(name1, name2)
                        .filter(s -> s != null && !s.isBlank())
                        .collect(Collectors.joining(" ")); //$NON-NLS-1$
                String seat = Stream.of(str, plz, ort)
                        .filter(s -> s != null && !s.isBlank())
                        .collect(Collectors.joining(" ")); //$NON-NLS-1$
                return CompanyData.builder()
                        .companyDescription(description)
                        .companySeat(seat)
                        .build();
            });
        } catch (Exception e) {
            log.warn("Could not fetch company data from {}.ZBEN1: {}", mainLibrary(), e.getMessage()); //$NON-NLS-1$
            return CompanyData.builder().companyDescription("").companySeat("").build(); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    // -------------------------------------------------------------------------
    // MIMV_ODABRANI_TIPOVI_OBVEZNIKA — Pantheon MSSQL (local, no linked server)
    // -------------------------------------------------------------------------

    /**
     * Returns the OIB_OBVEZNIKA from the existing MIMV_ODABRANI_TIPOVI_OBVEZNIKA row, or null if no row exists.
     * Used by saveTaxPayerType to avoid an AS400 round-trip when a row is already present.
     */
    public String fetchOibFromOdabraniTipoviObveznika() {
        try {
            List<String> result = jdbcTemplate.query(
                    "SELECT TOP 1 OIB_OBVEZNIKA FROM MIMV_ODABRANI_TIPOVI_OBVEZNIKA", //$NON-NLS-1$
                    (rs) -> {
                        if (rs.next()) {
                            return java.util.Collections.singletonList(rs.getString("OIB_OBVEZNIKA")); //$NON-NLS-1$
                        }
                        return java.util.Collections.emptyList();
                    });
            return result.isEmpty() ? null : result.get(0);
        } catch (Exception e) {
            log.warn("Could not fetch OIB from MIMV_ODABRANI_TIPOVI_OBVEZNIKA: {}", e.getMessage()); //$NON-NLS-1$
            return null;
        }
    }

    /**
     * Returns the list of active taxpayer type codes (MV01, MV02, MV03) from MIMV_ODABRANI_TIPOVI_OBVEZNIKA.
     * Uses SELECT TOP 1 without OIB filter — single-company app, exactly one row expected.
     * Reads BIT values by column index to avoid any column-name encoding issues.
     * Returns an empty list if no row exists or the query fails.
     */
    public List<String> fetchOdabraniTipoviObveznika() {
        String sql = "SELECT TOP 1 [MV01_PROIZVOĐAČ], MV02_TRGOVAC_NOVIM, MV03_TRGOVAC_RABLJENIM" //$NON-NLS-1$
                + " FROM MIMV_ODABRANI_TIPOVI_OBVEZNIKA"; //$NON-NLS-1$
        log.info("fetchOdabraniTipoviObveznika: executing query"); //$NON-NLS-1$
        try {
            return jdbcTemplate.query(sql, (rs) -> {
                List<String> types = new ArrayList<>();
                if (rs.next()) {
                    boolean mv01 = rs.getInt(1) != 0;
                    boolean mv02 = rs.getInt(2) != 0;
                    boolean mv03 = rs.getInt(3) != 0;
                    log.info("fetchOdabraniTipoviObveznika: mv01={}, mv02={}, mv03={}", mv01, mv02, mv03); //$NON-NLS-1$
                    if (mv01) types.add("MV01"); //$NON-NLS-1$
                    if (mv02) types.add("MV02"); //$NON-NLS-1$
                    if (mv03) types.add("MV03"); //$NON-NLS-1$
                } else {
                    log.warn("fetchOdabraniTipoviObveznika: no rows found in MIMV_ODABRANI_TIPOVI_OBVEZNIKA"); //$NON-NLS-1$
                }
                return types;
            });
        } catch (Exception e) {
            log.warn("fetchOdabraniTipoviObveznika failed: {}", e.getMessage()); //$NON-NLS-1$
            return new ArrayList<>();
        }
    }

    /**
     * Returns the list of active taxpayer type codes (MV01, MV02, MV03) for the given OIB.
     * Queries MIMV_ODABRANI_TIPOVI_OBVEZNIKA filtered by OIB_OBVEZNIKA.
     * Returns an empty list if the row does not exist or the query fails.
     */
    public List<String> fetchOdabraniTipoviObveznika(String oib) {
        String sql = "SELECT [MV01_PROIZVOĐAČ] AS mv01, MV02_TRGOVAC_NOVIM AS mv02, MV03_TRGOVAC_RABLJENIM AS mv03" //$NON-NLS-1$
                + " FROM MIMV_ODABRANI_TIPOVI_OBVEZNIKA WHERE OIB_OBVEZNIKA = ?"; //$NON-NLS-1$
        try {
            return jdbcTemplate.query(sql, (rs) -> {
                List<String> types = new ArrayList<>();
                if (rs.next()) {
                    if (rs.getBoolean("mv01")) types.add("MV01"); //$NON-NLS-1$ //$NON-NLS-2$
                    if (rs.getBoolean("mv02")) types.add("MV02"); //$NON-NLS-1$ //$NON-NLS-2$
                    if (rs.getBoolean("mv03")) types.add("MV03"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                return types;
            }, oib);
        } catch (Exception e) {
            log.warn("Could not fetch MIMV_ODABRANI_TIPOVI_OBVEZNIKA for oib=***: {}", e.getMessage()); //$NON-NLS-1$
            return new ArrayList<>();
        }
    }

    /**
     * Saves selected taxpayer types for the given OIB into MIMV_ODABRANI_TIPOVI_OBVEZNIKA.
     * Uses UPDATE if the row already exists, INSERT otherwise — avoids requiring DELETE permission.
     *
     * @param oib   OIB_OBVEZNIKA
     * @param mv01  true if MV01_PROIZVOĐAČ should be set
     * @param mv02  true if MV02_TRGOVAC_NOVIM should be set
     * @param mv03  true if MV03_TRGOVAC_RABLJENIM should be set
     */
    public void upsertOdabraniTipoviObveznika(String oib, boolean mv01, boolean mv02, boolean mv03) {
        int updated = jdbcTemplate.update(
                "UPDATE MIMV_ODABRANI_TIPOVI_OBVEZNIKA SET [MV01_PROIZVOĐAČ] = ?, MV02_TRGOVAC_NOVIM = ?, MV03_TRGOVAC_RABLJENIM = ?" //$NON-NLS-1$
                + " WHERE OIB_OBVEZNIKA = ?", //$NON-NLS-1$
                mv01 ? 1 : 0, mv02 ? 1 : 0, mv03 ? 1 : 0, oib);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO MIMV_ODABRANI_TIPOVI_OBVEZNIKA (OIB_OBVEZNIKA, [MV01_PROIZVOĐAČ], MV02_TRGOVAC_NOVIM, MV03_TRGOVAC_RABLJENIM)" //$NON-NLS-1$
                    + " VALUES (?, ?, ?, ?)", //$NON-NLS-1$
                    oib, mv01 ? 1 : 0, mv02 ? 1 : 0, mv03 ? 1 : 0);
        }
        log.info("Saved MIMV_ODABRANI_TIPOVI_OBVEZNIKA for oib=***: mv01={}, mv02={}, mv03={}", mv01, mv02, mv03); //$NON-NLS-1$
    }

    // -------------------------------------------------------------------------
    // MIMV_ZAGLAVLJE — Pantheon MSSQL (local, no linked server)
    // -------------------------------------------------------------------------

    /**
     * Deletes all MIMV_ZAGLAVLJE rows matching the given form primary key.
     * Called before re-inserting to ensure idempotency.
     */
    public void deleteMimvZaglavlje(String oib, int formDate, String sifobr, int seqNum, int versionNum) {
        int rows = jdbcTemplate.update(
                "DELETE FROM " + MIMV_ZAGLAVLJE //$NON-NLS-1$
                + " WHERE OIB_OBVEZNIKA = ? AND DATUM_PP = ? AND SIFRA_OBRASCA_PP = ? AND REDNI_BROJ_PP = ? AND REDNI_BROJ_PP_PROM = ?", //$NON-NLS-1$
                oib, formDate, sifobr, seqNum, versionNum);
        log.info("Deleted {} existing MIMV_ZAGLAVLJE row(s) for oib=***, formDate={}, sifobr={}", rows, formDate, sifobr); //$NON-NLS-1$
    }

    /**
     * Inserts a single MIMV_ZAGLAVLJE header row from the provided request and computed totals.
     *
     * @param oib           OIB_OBVEZNIKA (fetched from AS400)
     * @param formDate      DATUM_PP as integer YYYYMMDD
     * @param sifobr        SIFRA_OBRASCA_PP (401 or 405)
     * @param compositeId   IDENTIFIKATOR (built from key fields)
     * @param request       source of all remaining header fields
     * @param totalNew      UKUP_IZNOS_NOVA — computed from MIMV_DETALJ
     * @param totalUsed     UKUP_IZNOS_RABLJENA — computed from MIMV_DETALJ
     */
    public void insertMimvZaglavlje(String oib, int formDate, String sifobr, String compositeId,
                                    FormBuildRequest request, String companyDescription, String companySeat,
                                    BigDecimal totalNew, BigDecimal totalUsed) {
        BigDecimal totalAll = totalNew.add(totalUsed);
        String action = request.getActionCode() != null ? request.getActionCode() : "N"; //$NON-NLS-1$
        int seqNum = request.getSequentialNumberInPeriod() != null ? request.getSequentialNumberInPeriod() : 1;
        int versionNum = request.getVersionNumber() != null ? request.getVersionNumber() : 1;
        Integer obdobljeOd = request.getDateFrom() != null ? Integer.parseInt(request.getDateFrom().format(YYYYMMDD)) : null;
        Integer obdobljeDo = request.getDateTo()   != null ? Integer.parseInt(request.getDateTo().format(YYYYMMDD))   : null;

        jdbcTemplate.update(
                "INSERT INTO " + MIMV_ZAGLAVLJE //$NON-NLS-1$
                + " (OIB_OBVEZNIKA, DATUM_PP, SIFRA_OBRASCA_PP, REDNI_BROJ_PP, REDNI_BROJ_PP_PROM," //$NON-NLS-1$
                + " IDENTIFIKATOR, AKCIJA_PP, RAZDOBLJE_OD, RAZDOBLJE_DO," //$NON-NLS-1$
                + " CARINSKI_URED, CARINSKI_URED_OPIS, NAZIV_OBVEZNIKA, SJEDISTE_OBVEZNIKA," //$NON-NLS-1$
                + " EMAIL_ADRESA, ODGOVORNA_OSOBA, TIPOVI_OBVEZNIKA," //$NON-NLS-1$
                + " UKUP_IZNOS_NOVA, UKUP_IZNOS_RABLJENA, UKUP_IZNOS_NOVA_I_RAB)" //$NON-NLS-1$
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", //$NON-NLS-1$
                oib, formDate, sifobr, seqNum, versionNum,
                compositeId, action, obdobljeOd, obdobljeDo,
                request.getTaxOfficeCode(), request.getTaxOfficeDescription(),
                companyDescription, companySeat,
                request.getDestinationEmail(), request.getResponsiblePerson(),
                request.getTaxPayerCode(),
                totalNew, totalUsed, totalAll);

        log.info("Inserted MIMV_ZAGLAVLJE: id={}, totalNew={}, totalUsed={}", compositeId, totalNew, totalUsed); //$NON-NLS-1$
    }

    /**
     * Updates the three total columns on an existing MIMV_ZAGLAVLJE row.
     * Called after MIMV_DETALJ rows are inserted and summed, since ZAGLAVLJE must
     * exist before DETALJ due to FK_DETALJ_ZAGLAVLJE.
     */
    public void updateMimvZaglavljeTotals(String oib, int formDate, String sifobr, int seqNum, int versionNum,
                                           BigDecimal totalNew, BigDecimal totalUsed) {
        BigDecimal totalAll = totalNew.add(totalUsed);
        int rows = jdbcTemplate.update(
                "UPDATE " + MIMV_ZAGLAVLJE //$NON-NLS-1$
                + " SET UKUP_IZNOS_NOVA = ?, UKUP_IZNOS_RABLJENA = ?, UKUP_IZNOS_NOVA_I_RAB = ?" //$NON-NLS-1$
                + " WHERE OIB_OBVEZNIKA = ? AND DATUM_PP = ? AND SIFRA_OBRASCA_PP = ? AND REDNI_BROJ_PP = ? AND REDNI_BROJ_PP_PROM = ?", //$NON-NLS-1$
                totalNew, totalUsed, totalAll, oib, formDate, sifobr, seqNum, versionNum);
        log.info("Updated MIMV_ZAGLAVLJE totals ({} row): totalNew={}, totalUsed={}", rows, totalNew, totalUsed); //$NON-NLS-1$
    }

    /**
     * Fetches all MIMV_ZAGLAVLJE rows ordered by DATUM_PP descending.
     * Used by GET /mimv/preview/existing.
     */
    public List<MimvProcessedItem> fetchAllMimvZaglavlje() {
        String sql = "SELECT OIB_OBVEZNIKA, DATUM_PP, SIFRA_OBRASCA_PP, REDNI_BROJ_PP, REDNI_BROJ_PP_PROM," //$NON-NLS-1$
                + " IDENTIFIKATOR, AKCIJA_PP, RAZDOBLJE_OD, RAZDOBLJE_DO," //$NON-NLS-1$
                + " CARINSKI_URED, NAZIV_OBVEZNIKA," //$NON-NLS-1$
                + " UKUP_IZNOS_NOVA, UKUP_IZNOS_RABLJENA, UKUP_IZNOS_NOVA_I_RAB" //$NON-NLS-1$
                + " FROM " + MIMV_ZAGLAVLJE //$NON-NLS-1$
                + " ORDER BY DATUM_PP DESC"; //$NON-NLS-1$

        log.debug("fetchAllMimvZaglavlje SQL: {}", sql); //$NON-NLS-1$

        return jdbcTemplate.query(sql, (rs, rowNum) -> MimvProcessedItem.builder()
                .oibObveznika(trim(rs.getString("OIB_OBVEZNIKA"))) //$NON-NLS-1$
                .sifraObrascaPP(trim(rs.getString("SIFRA_OBRASCA_PP"))) //$NON-NLS-1$
                .redniBrojPP(nullableInt(rs, "REDNI_BROJ_PP")) //$NON-NLS-1$
                .redniBrojPPPromjena(nullableInt(rs, "REDNI_BROJ_PP_PROM")) //$NON-NLS-1$
                .identificator(trim(rs.getString("IDENTIFIKATOR"))) //$NON-NLS-1$
                .action(trim(rs.getString("AKCIJA_PP"))) //$NON-NLS-1$
                .dateFrom(toLocalDate(nullableInt(rs, "RAZDOBLJE_OD"))) //$NON-NLS-1$
                .dateTo(toLocalDate(nullableInt(rs, "RAZDOBLJE_DO"))) //$NON-NLS-1$
                .carinskiUred(trim(rs.getString("CARINSKI_URED"))) //$NON-NLS-1$
                .nazivObveznika(trim(rs.getString("NAZIV_OBVEZNIKA"))) //$NON-NLS-1$
                .ukupIznosNova(rs.getBigDecimal("UKUP_IZNOS_NOVA")) //$NON-NLS-1$
                .ukupIznosRabljena(rs.getBigDecimal("UKUP_IZNOS_RABLJENA")) //$NON-NLS-1$
                .ukupIznosNovaIRab(rs.getBigDecimal("UKUP_IZNOS_NOVA_I_RAB")) //$NON-NLS-1$
                .build());
    }

    // -------------------------------------------------------------------------
    // MIMV_DETALJ — Pantheon MSSQL with data sourced from AS400 via linked server
    // -------------------------------------------------------------------------

    /**
     * Deletes all MIMV_DETALJ rows matching the given form primary key.
     * Called before re-inserting to ensure idempotency.
     */
    public void deleteMimvDetalj(String oib, int formDate, String sifobr, int seqNum, int versionNum) {
        int rows = jdbcTemplate.update(
                "DELETE FROM " + MIMV_DETALJ //$NON-NLS-1$
                + " WHERE OIB_OBVEZNIKA = ? AND DATUM_PP = ? AND SIFRA_OBRASCA_PP = ? AND REDNI_BROJ_PP = ? AND REDNI_BROJ_PP_PROM = ?", //$NON-NLS-1$
                oib, formDate, sifobr, seqNum, versionNum);
        log.info("Deleted {} existing MIMV_DETALJ row(s) for oib=***, formDate={}, sifobr={}", rows, formDate, sifobr); //$NON-NLS-1$
    }

    /**
     * Inserts vehicle detail rows into MIMV_DETALJ.
     *
     * Two-step approach to avoid DTC (Distributed Transaction Coordinator):
     * 1. Execute the T-SQL SELECT directly against Pantheon MSSQL — AS400 HF tables are
     *    accessed via 4-part linked server naming ([linkedServer].[catalog].[library].[table]).
     *    The vehicle type filter uses MIMV_ODABRANI_TIPOVI_OBVEZNIKA (local MSSQL table).
     *    No EXEC AT is used, so no DTC is triggered.
     * 2. Batch INSERT the collected rows into MIMV_DETALJ locally.
     *
     * @throws IllegalStateException if linked server or HF library is not configured
     */
    public void insertMimvDetalj(String mandatorId, String oib, int formDate, String sifobr,
                                  int seqNum, int versionNum,
                                  String dateFrom, String dateTo) {
        boolean direct = direct();
        String linkedServer = direct ? null : linkedServer();
        String hfLibrary = settings().hfLibrary();
        if (!direct && (linkedServer == null || linkedServer.isBlank())) {
            throw new IllegalStateException("AS400 linked server is required for MIMV_DETALJ insert. " //$NON-NLS-1$
                    + "Configure AS400_LINKED_SERVER or _cdp_param.aclinkserver."); //$NON-NLS-1$
        }
        if (hfLibrary == null || hfLibrary.isBlank()) {
            throw new IllegalStateException("AS400 HF library is required for MIMV_DETALJ insert. " //$NON-NLS-1$
                    + "Configure AS400_DATA_GROUP (e.g. KB0)."); //$NON-NLS-1$
        }

        // Step 1: execute T-SQL with 4-part linked server names directly — no EXEC AT, no DTC
        String tsql = loadDetaljSelectSql(mandatorId, oib, formDate, sifobr,
                seqNum, versionNum, dateFrom, dateTo);

        List<Object[]> rows = as400().query(tsql, (rs, rowNum) -> {
            Object[] row = new Object[30];
            for (int i = 0; i < 30; i++) {
                row[i] = rs.getObject(i + 1);
            }
            row[9] = withCroatianSuffix(row[9]); // TIP_VARIJANTA_TRG_NAZIV
            return row;
        });
        log.info("Fetched {} rows from AS400 for MIMV_DETALJ insert ({})", rows.size(), //$NON-NLS-1$
                direct ? "direct AS400" : "linked server " + linkedServer); //$NON-NLS-1$ //$NON-NLS-2$

        if (rows.isEmpty()) {
            log.info("No AS400 rows to insert into MIMV_DETALJ"); //$NON-NLS-1$
            return;
        }

        // Step 2: batch INSERT locally — no remote server involved, no DTC needed
        String insertSql = "INSERT INTO " + MIMV_DETALJ //$NON-NLS-1$
                + " (OIB_OBVEZNIKA, DATUM_PP, SIFRA_OBRASCA_PP, REDNI_BROJ_PP, REDNI_BROJ_PP_PROM," //$NON-NLS-1$
                + " SIFRA_VOZILA, STATUS_VOZILA, VRSTA_VOZILA, MARKA_VOZILA, TIP_VARIJANTA_TRG_NAZIV," //$NON-NLS-1$
                + " VIN_OZNAKA, VRSTA_GORIVA, DATUM_PRVE_REGISTRACIJE, PROSJ_EMISIJA_CO2, RAZINA_EMISIJE," //$NON-NLS-1$
                + " RADNI_OBUJAM_MOTORA, SNAGA_MOTORA, PRODAJNA_CIJENA, BROJ_PRIJEDJENIH_KM," //$NON-NLS-1$
                + " KAMPER, PLUG_IN, VOZILO_71, VOZILO_81, TESTNO_VOZILO, DEPRECIJACIJA," //$NON-NLS-1$
                + " POREZNI_OBVEZNIK, OIB, BROJ_RACUNA, DATUM_IZDAVANJA_RACUNA, OBRACUNATI_IZNOS_PP)" //$NON-NLS-1$
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"; //$NON-NLS-1$

        jdbcTemplate.batchUpdate(insertSql, rows);
        log.info("Batch-inserted {} rows into MIMV_DETALJ for formDate={}, sifobr={}", rows.size(), formDate, sifobr); //$NON-NLS-1$
    }

    /**
     * The vehicle-name SQL appends the gearbox and paint type in plain ASCII ("rucni, obicna"),
     * because accented literals do not survive every AS400 job CCSID. IVAS writes them with
     * Croatian letters, so they are fixed here: rucni -> ručni, obicna -> obična.
     */
    static Object withCroatianSuffix(Object value) {
        if (!(value instanceof String s)) {
            return value;
        }
        return s.replace(", rucni,", ", ručni,") //$NON-NLS-1$ //$NON-NLS-2$
                .replaceAll(", obicna$", ", obična"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Computes the sums of OBRACUNATI_IZNOS_PP grouped by vehicle status
     * from the just-inserted MIMV_DETALJ rows.
     *
     * @return BigDecimal[2] where [0]=total new vehicles, [1]=total used vehicles
     */
    public BigDecimal[] sumMimvDetaljTotals(String oib, int formDate, String sifobr, int seqNum, int versionNum) {
        String sql = "SELECT" //$NON-NLS-1$
                + " SUM(CASE WHEN STATUS_VOZILA = 'N' THEN OBRACUNATI_IZNOS_PP ELSE 0 END) AS total_nova," //$NON-NLS-1$
                + " SUM(CASE WHEN STATUS_VOZILA IN ('R', 'NT') THEN OBRACUNATI_IZNOS_PP ELSE 0 END) AS total_rabljena" //$NON-NLS-1$
                + " FROM " + MIMV_DETALJ //$NON-NLS-1$
                + " WHERE OIB_OBVEZNIKA = ? AND DATUM_PP = ? AND SIFRA_OBRASCA_PP = ? AND REDNI_BROJ_PP = ? AND REDNI_BROJ_PP_PROM = ?"; //$NON-NLS-1$

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            BigDecimal nova = rs.getBigDecimal("total_nova"); //$NON-NLS-1$
            BigDecimal rabljena = rs.getBigDecimal("total_rabljena"); //$NON-NLS-1$
            return new BigDecimal[]{
                nova != null ? nova : BigDecimal.ZERO,
                rabljena != null ? rabljena : BigDecimal.ZERO
            };
        }, oib, formDate, sifobr, seqNum, versionNum);
    }

    /**
     * Fetches all MIMV_DETALJ rows for the given form primary key.
     * Called after insertMimvDetalj to include detail rows in the build-form response.
     */
    public List<MimvDetaljItem> fetchMimvDetalj(String oib, int formDate, String sifobr, int seqNum, int versionNum) {
        String sql = "SELECT OIB_OBVEZNIKA, DATUM_PP, SIFRA_OBRASCA_PP, REDNI_BROJ_PP, REDNI_BROJ_PP_PROM," //$NON-NLS-1$
                + " SIFRA_VOZILA, STATUS_VOZILA, VRSTA_VOZILA, MARKA_VOZILA, TIP_VARIJANTA_TRG_NAZIV," //$NON-NLS-1$
                + " VIN_OZNAKA, VRSTA_GORIVA, DATUM_PRVE_REGISTRACIJE, PROSJ_EMISIJA_CO2, RAZINA_EMISIJE," //$NON-NLS-1$
                + " RADNI_OBUJAM_MOTORA, SNAGA_MOTORA, PRODAJNA_CIJENA, BROJ_PRIJEDJENIH_KM," //$NON-NLS-1$
                + " KAMPER, PLUG_IN, VOZILO_71, VOZILO_81, TESTNO_VOZILO, DEPRECIJACIJA," //$NON-NLS-1$
                + " POREZNI_OBVEZNIK, OIB, BROJ_RACUNA, DATUM_IZDAVANJA_RACUNA, OBRACUNATI_IZNOS_PP" //$NON-NLS-1$
                + " FROM " + MIMV_DETALJ //$NON-NLS-1$
                + " WHERE OIB_OBVEZNIKA = ? AND DATUM_PP = ? AND SIFRA_OBRASCA_PP = ? AND REDNI_BROJ_PP = ? AND REDNI_BROJ_PP_PROM = ?" //$NON-NLS-1$
                + " ORDER BY DATUM_IZDAVANJA_RACUNA"; //$NON-NLS-1$

        log.debug("fetchMimvDetalj SQL: {}", sql); //$NON-NLS-1$

        return jdbcTemplate.query(sql, (rs, rowNum) -> MimvDetaljItem.builder()
                .oibObveznika(trim(rs.getString("OIB_OBVEZNIKA"))) //$NON-NLS-1$
                .datumPP(nullableInt(rs, "DATUM_PP")) //$NON-NLS-1$
                .sifraObrascaPP(trim(rs.getString("SIFRA_OBRASCA_PP"))) //$NON-NLS-1$
                .redniBrojPP(nullableInt(rs, "REDNI_BROJ_PP")) //$NON-NLS-1$
                .redniBrojPPProm(nullableInt(rs, "REDNI_BROJ_PP_PROM")) //$NON-NLS-1$
                .sifraVozila(trim(rs.getString("SIFRA_VOZILA"))) //$NON-NLS-1$
                .statusVozila(trim(rs.getString("STATUS_VOZILA"))) //$NON-NLS-1$
                .vrstaVozila(trim(rs.getString("VRSTA_VOZILA"))) //$NON-NLS-1$
                .markaVozila(trim(rs.getString("MARKA_VOZILA"))) //$NON-NLS-1$
                .tipVarijantaTrgNaziv(trim(rs.getString("TIP_VARIJANTA_TRG_NAZIV"))) //$NON-NLS-1$
                .vinOznaka(trim(rs.getString("VIN_OZNAKA"))) //$NON-NLS-1$
                .vrstaGoriva(trim(rs.getString("VRSTA_GORIVA"))) //$NON-NLS-1$
                .datumPrveRegistracije(nullableInt(rs, "DATUM_PRVE_REGISTRACIJE")) //$NON-NLS-1$
                .prosjEmisijaCO2(rs.getBigDecimal("PROSJ_EMISIJA_CO2")) //$NON-NLS-1$
                .razinaEmisije(trim(rs.getString("RAZINA_EMISIJE"))) //$NON-NLS-1$
                .radniObujamMotora(rs.getBigDecimal("RADNI_OBUJAM_MOTORA")) //$NON-NLS-1$
                .snagaMotora(rs.getBigDecimal("SNAGA_MOTORA")) //$NON-NLS-1$
                .prodajnaCijena(rs.getBigDecimal("PRODAJNA_CIJENA")) //$NON-NLS-1$
                .brojPrijedjenihKm(rs.getBigDecimal("BROJ_PRIJEDJENIH_KM")) //$NON-NLS-1$
                .kamper(trim(rs.getString("KAMPER"))) //$NON-NLS-1$
                .plugIn(nullableInt(rs, "PLUG_IN")) //$NON-NLS-1$
                .vozilo71(trim(rs.getString("VOZILO_71"))) //$NON-NLS-1$
                .vozilo81(trim(rs.getString("VOZILO_81"))) //$NON-NLS-1$
                .testnoVozilo(rs.getBigDecimal("TESTNO_VOZILO")) //$NON-NLS-1$
                .deprecijacija(rs.getBigDecimal("DEPRECIJACIJA")) //$NON-NLS-1$
                .porezniObveznik(trim(rs.getString("POREZNI_OBVEZNIK"))) //$NON-NLS-1$
                .oib(trim(rs.getString("OIB"))) //$NON-NLS-1$
                .brojRacuna(trim(rs.getString("BROJ_RACUNA"))) //$NON-NLS-1$
                .datumIzdavanjaRacuna(nullableInt(rs, "DATUM_IZDAVANJA_RACUNA")) //$NON-NLS-1$
                .obracunatiIznosPP(rs.getBigDecimal("OBRACUNATI_IZNOS_PP")) //$NON-NLS-1$
                .build(),
                oib, formDate, sifobr, seqNum, versionNum);
    }

    // -------------------------------------------------------------------------
    // Update-flow inserts — data supplied by caller, no AS400 reads
    // -------------------------------------------------------------------------

    /**
     * Inserts a MIMV_ZAGLAVLJE row in the update flow.
     * The form key (oib, formDate, sifobr, seqNum, versionNum) and compositeId are resolved
     * by the caller; mutable display fields come from {@link com.smit.mimw.dto.ZaglavljeUpdateData}.
     * Pass {@code BigDecimal.ZERO} for totals as a placeholder; call
     * {@link #updateMimvZaglavljeTotals} after DETALJ rows are summed.
     *
     * @param oib          OIB_OBVEZNIKA (fetched from AS400 by the service)
     * @param formDate     DATUM_PP as YYYYMMDD integer
     * @param sifobr       SIFRA_OBRASCA_PP (401 or 405)
     * @param seqNum       REDNI_BROJ_PP
     * @param versionNum   REDNI_BROJ_PP_PROM
     * @param compositeId  IDENTIFIKATOR (pre-built composite key string)
     * @param taxPayerCode TIPOVI_OBVEZNIKA (MV01/MV02/MV03)
     * @param z            mutable display fields from the request body
     * @param totalNew     UKUP_IZNOS_NOVA placeholder
     * @param totalUsed    UKUP_IZNOS_RABLJENA placeholder
     */
    public void insertMimvZaglavljeFromZaglavlje(String oib, int formDate, String sifobr,
                                                  int seqNum, int versionNum, String compositeId,
                                                  String taxPayerCode,
                                                  com.smit.mimw.dto.ZaglavljeUpdateData z,
                                                  BigDecimal totalNew, BigDecimal totalUsed)
    {
        BigDecimal totalAll = totalNew.add(totalUsed);
        String action      = z.getActionCode() != null ? z.getActionCode() : "N"; //$NON-NLS-1$
        Integer obdobljeOd = z.getDateFrom() != null ? Integer.parseInt(z.getDateFrom().format(YYYYMMDD)) : null;
        Integer obdobljeDo = z.getDateTo()   != null ? Integer.parseInt(z.getDateTo().format(YYYYMMDD))   : null;

        jdbcTemplate.update(
                "INSERT INTO " + MIMV_ZAGLAVLJE //$NON-NLS-1$
                + " (OIB_OBVEZNIKA, DATUM_PP, SIFRA_OBRASCA_PP, REDNI_BROJ_PP, REDNI_BROJ_PP_PROM," //$NON-NLS-1$
                + " IDENTIFIKATOR, AKCIJA_PP, RAZDOBLJE_OD, RAZDOBLJE_DO," //$NON-NLS-1$
                + " CARINSKI_URED, CARINSKI_URED_OPIS, NAZIV_OBVEZNIKA, SJEDISTE_OBVEZNIKA," //$NON-NLS-1$
                + " EMAIL_ADRESA, ODGOVORNA_OSOBA, TIPOVI_OBVEZNIKA," //$NON-NLS-1$
                + " UKUP_IZNOS_NOVA, UKUP_IZNOS_RABLJENA, UKUP_IZNOS_NOVA_I_RAB)" //$NON-NLS-1$
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", //$NON-NLS-1$
                oib, formDate, sifobr, seqNum, versionNum,
                compositeId, action, obdobljeOd, obdobljeDo,
                z.getTaxOfficeCode(), z.getTaxOfficeDescription(),
                z.getCompanyDescription(), z.getCompanySeat(),
                z.getDestinationEmail(), z.getResponsiblePerson(),
                taxPayerCode,
                totalNew, totalUsed, totalAll);

        log.info("Inserted MIMV_ZAGLAVLJE (update flow): id={}", compositeId); //$NON-NLS-1$
    }

    /**
     * Batch-inserts MIMV_DETALJ rows from a caller-supplied list.
     * Key fields (oib, formDate, sifobr, seqNum, versionNum) are taken from the
     * zaglavlje parameters; any key values present on the individual items are ignored.
     *
     * @param items      vehicle detail rows to persist
     * @param oib        OIB_OBVEZNIKA (form key)
     * @param formDate   DATUM_PP as YYYYMMDD integer (form key)
     * @param sifobr     SIFRA_OBRASCA_PP (form key)
     * @param seqNum     REDNI_BROJ_PP (form key)
     * @param versionNum REDNI_BROJ_PP_PROM (form key)
     */
    public void insertMimvDetaljFromItems(List<com.smit.mimw.dto.MimvDetaljItem> items,
                                           String oib, int formDate, String sifobr,
                                           int seqNum, int versionNum)
    {
        if (items == null || items.isEmpty())
        {
            log.info("No MIMV_DETALJ items to insert (update flow)"); //$NON-NLS-1$
            return;
        }

        String insertSql = "INSERT INTO " + MIMV_DETALJ //$NON-NLS-1$
                + " (OIB_OBVEZNIKA, DATUM_PP, SIFRA_OBRASCA_PP, REDNI_BROJ_PP, REDNI_BROJ_PP_PROM," //$NON-NLS-1$
                + " SIFRA_VOZILA, STATUS_VOZILA, VRSTA_VOZILA, MARKA_VOZILA, TIP_VARIJANTA_TRG_NAZIV," //$NON-NLS-1$
                + " VIN_OZNAKA, VRSTA_GORIVA, DATUM_PRVE_REGISTRACIJE, PROSJ_EMISIJA_CO2, RAZINA_EMISIJE," //$NON-NLS-1$
                + " RADNI_OBUJAM_MOTORA, SNAGA_MOTORA, PRODAJNA_CIJENA, BROJ_PRIJEDJENIH_KM," //$NON-NLS-1$
                + " KAMPER, PLUG_IN, VOZILO_71, VOZILO_81, TESTNO_VOZILO, DEPRECIJACIJA," //$NON-NLS-1$
                + " POREZNI_OBVEZNIK, OIB, BROJ_RACUNA, DATUM_IZDAVANJA_RACUNA, OBRACUNATI_IZNOS_PP)" //$NON-NLS-1$
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"; //$NON-NLS-1$

        List<Object[]> rows = items.stream().map(item -> new Object[]{
                oib, formDate, sifobr, seqNum, versionNum,
                item.getSifraVozila(), item.getStatusVozila(), item.getVrstaVozila(),
                item.getMarkaVozila(), item.getTipVarijantaTrgNaziv(), item.getVinOznaka(),
                item.getVrstaGoriva(), item.getDatumPrveRegistracije(), item.getProsjEmisijaCO2(),
                item.getRazinaEmisije(), item.getRadniObujamMotora(), item.getSnagaMotora(),
                item.getProdajnaCijena(), item.getBrojPrijedjenihKm(), item.getKamper(),
                item.getPlugIn(), item.getVozilo71(), item.getVozilo81(),
                item.getTestnoVozilo(), item.getDeprecijacija(), item.getPorezniObveznik(),
                item.getOib(), item.getBrojRacuna(), item.getDatumIzdavanjaRacuna(),
                item.getObracunatiIznosPP()
        }).collect(Collectors.toList());

        jdbcTemplate.batchUpdate(insertSql, rows);
        log.info("Batch-inserted {} rows into MIMV_DETALJ (update flow)", rows.size()); //$NON-NLS-1$
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads insert_mimv_detalj.sql from the classpath, strips comment lines,
     * and replaces all runtime placeholders with the provided values.
     * Returns a ready-to-execute T-SQL SELECT string.
     */
    private String loadDetaljSelectSql(String mandatorId, String oib, int formDate, String sifobr,
                                        int seqNum, int versionNum,
                                        String dateFrom, String dateTo) {
        // Outside the try: a wrong AS400_BRANCH must reach the user as a configuration error (503 with message)
        Integer branch = settings().branchFilter();
        try {
            byte[] bytes = getClass().getClassLoader()
                    .getResourceAsStream(direct() ? "sql/insert_mimv_detalj_db2.sql" : "sql/insert_mimv_detalj.sql") //$NON-NLS-1$ //$NON-NLS-2$
                    .readAllBytes();
            String raw = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

            StringBuilder sb = new StringBuilder();
            for (String line : raw.split("\n")) { //$NON-NLS-1$
                if (!line.trim().startsWith("--")) { //$NON-NLS-1$
                    sb.append(line).append("\n"); //$NON-NLS-1$
                }
            }
            String sql = sb.toString();

            TenantSettings s = settings();
            String linkedServer = s.directAs400() ? null : linkedServer();
            sql = sql.replace("<LINKEDSERVER>",  linkedServer != null ? linkedServer : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<CATALOG>",       s.catalog() != null ? s.catalog() : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<MAINLIBRARY>",   s.mainLibrary() != null ? s.mainLibrary() : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<PLIBRARY>",      s.pLibrary() != null ? s.pLibrary() : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<HFLIBRARY>",     s.hfLibrary() != null ? s.hfLibrary() : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<SIFPOD>",       mandatorId != null ? mandatorId : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<OIB>",          oib != null ? oib : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<DATUM>",        String.valueOf(formDate)); //$NON-NLS-1$
            sql = sql.replace("<SIFOBR>",       sifobr != null ? sifobr : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<RBR>",          String.valueOf(seqNum)); //$NON-NLS-1$
            sql = sql.replace("<RBRPROM>",      String.valueOf(versionNum)); //$NON-NLS-1$
            sql = sql.replace("<ODDATUMA>",     dateFrom != null ? dateFrom : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<DODATUMA>",     dateTo != null ? dateTo : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<BRANCHFILTER>", branch != null //$NON-NLS-1$
                    ? "AND CASE WHEN t1.AENBET > 0 THEN t1.AENBET ELSE t1.ANLBET END = " + branch //$NON-NLS-1$
                    : ""); //$NON-NLS-1$
            if (s.directAs400()) {
                // MIMV_ODABRANI_TIPOVI_OBVEZNIKA is a SQL Server table, not on AS400: resolve the flags here
                List<String> types = fetchOdabraniTipoviObveznika();
                sql = sql.replace("<MV02>", types.contains("MV02") ? "1 = 1" : "1 = 0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                sql = sql.replace("<MV03>", types.contains("MV03") ? "1 = 1" : "1 = 0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            }
            log.info("MIMV_DETALJ vehicles: data group {}, branch {}", s.dataGroup(), //$NON-NLS-1$
                    branch != null ? branch : "all"); //$NON-NLS-1$

            return sql.trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load the MIMV_DETALJ select SQL", e); //$NON-NLS-1$
        }
    }

    private static String trim(String s) {
        return s != null ? s.trim() : null;
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    /**
     * Converts a YYYYMMDD integer to a LocalDate. Returns null for 0 or invalid values.
     */
    private static LocalDate toLocalDate(Integer yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd == 0) {
            return null;
        }
        try {
            return LocalDate.of(yyyymmdd / 10000, (yyyymmdd / 100) % 100, yyyymmdd % 100);
        } catch (Exception e) {
            return null;
        }
    }
}
