package com.smit.mimw.repository;

import com.smit.mimw.dto.Brand;
import com.smit.mimw.dto.CompanyData;
import com.smit.mimw.dto.FormBuildRequest;
import com.smit.mimw.dto.MimvDetaljItem;
import com.smit.mimw.dto.MimvProcessedItem;
import com.smit.mimw.dto.TaxOffice;
import com.smit.mimw.dto.TaxPayerType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
 */
@Repository
public class As400Repository {

    private static final Logger log = LoggerFactory.getLogger(As400Repository.class);

    private static final String MIMV_ZAGLAVLJE = "MIMV_ZAGLAVLJE"; //$NON-NLS-1$
    private static final String MIMV_DETALJ = "MIMV_DETALJ"; //$NON-NLS-1$

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd"); //$NON-NLS-1$

    private final JdbcTemplate jdbcTemplate;

    /**
     * Linked server name — set via env var AS400_LINKED_SERVER,
     * or auto-read from _cdp_param.aclinkserver at startup.
     */
    @Value("${as400.linked-server:}")
    private String linkedServer;

    /** AS400 catalog/database name — default ADRIAVC1. */
    @Value("${as400.catalog:ADRIAVC1}")
    private String catalog;

    public As400Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * If linkedServer was not provided via env var, try to read it from
     * _cdp_param — the same table Pantheon reads on startup.
     */
    @PostConstruct
    public void init() {
        if (linkedServer == null || linkedServer.isBlank()) {
            try {
                String ls = jdbcTemplate.queryForObject(
                        "SELECT TOP 1 aclinkserver FROM _cdp_param", String.class); //$NON-NLS-1$
                if (ls != null && !ls.isBlank()) {
                    linkedServer = ls.trim();
                    log.info("Loaded linked server name from _cdp_param: '{}'", linkedServer); //$NON-NLS-1$
                } else {
                    log.error("_cdp_param.aclinkserver is empty. Set env var AS400_LINKED_SERVER or populate _cdp_param."); //$NON-NLS-1$
                }
            } catch (Exception e) {
                log.error("Could not read linked server from _cdp_param ({}). Set env var AS400_LINKED_SERVER.", e.getMessage()); //$NON-NLS-1$
            }
        } else {
            log.info("Using linked server from env var: '{}'", linkedServer); //$NON-NLS-1$
        }
    }

    // -------------------------------------------------------------------------
    // AS400 table reference helper
    // -------------------------------------------------------------------------

    /**
     * Builds the fully qualified four-part table name for AS400 via linked server.
     * Format: [linkedServer].[catalog].[library].[table]
     */
    private String as400Table(String library, String table) {
        if (linkedServer == null || linkedServer.isBlank()) {
            return library + "." + table; //$NON-NLS-1$
        }
        return "[" + linkedServer + "].[" + catalog + "].[" + library + "].[" + table + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
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
        return jdbcTemplate.query(sql, (rs, rowNum) ->
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
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                Brand.builder()
                        .code(trim(rs.getString("CHYSAA"))) //$NON-NLS-1$
                        .description(trim(rs.getString("CHL6AQ"))) //$NON-NLS-1$
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Taxpayer types — AS400 IVAS0000B0.IVASDET
    // -------------------------------------------------------------------------

    /**
     * Fetches MIMV taxpayer type codes and descriptions from AS400 IVASDET.
     * Codes (MV01, MV02, MV03) and their descriptions are packed in a single row.
     */
    public List<TaxPayerType> fetchTaxPayerTypes() {
        String sql = "SELECT TOP 1 AUHHAP, AUK5TT, AUK6TT, AUK7TT, AUK8TT FROM " //$NON-NLS-1$
                + as400Table("IVAS0000B0", "IVASDET") //$NON-NLS-1$ //$NON-NLS-2$
                + " WHERE aupgm = 'KMDPDFR'"; //$NON-NLS-1$
        log.debug("fetchTaxPayerTypes SQL: {}", sql); //$NON-NLS-1$

        return jdbcTemplate.query(sql, rs -> {
            List<TaxPayerType> result = new ArrayList<>();
            if (rs.next()) {
                String auhhap = rs.getString("AUHHAP"); //$NON-NLS-1$
                String[] descColumns = {
                    rs.getString("AUK5TT"), //$NON-NLS-1$
                    rs.getString("AUK6TT"), //$NON-NLS-1$
                    rs.getString("AUK7TT"), //$NON-NLS-1$
                    rs.getString("AUK8TT")  //$NON-NLS-1$
                };
                if (auhhap != null && !auhhap.isBlank()) {
                    for (String raw : auhhap.trim().split(",")) { //$NON-NLS-1$
                        String code = raw.trim();
                        if (code.isEmpty()) {
                            continue;
                        }
                        String description = extractDescription(code, descColumns);
                        result.add(TaxPayerType.builder()
                                .taxPayerCode(code)
                                .taxPayerDescription(description)
                                .selected(false)
                                .build());
                    }
                }
            }
            return result;
        });
    }

    // -------------------------------------------------------------------------
    // OIB — AS400 KB0D1.HDB
    // -------------------------------------------------------------------------

    /**
     * Fetches the taxpayer OIB from KB0D1.HDB.
     * OIB occupies bytes 324-334 (length 11) inside the HDBINHALT packed field.
     * Uses SUBSTRING (T-SQL) since the query goes through the linked server.
     */
    public String fetchOib() {
        String sql = "SELECT TRIM(SUBSTRING(HDBINHALT, 324, 11)) AS OIB FROM " //$NON-NLS-1$
                + as400Table("KB0D1", "HDB") + " WHERE HDBSART = 'BEN'"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        log.debug("fetchOib SQL: {}", sql); //$NON-NLS-1$
        try {
            List<String> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("OIB")); //$NON-NLS-1$
            if (results.isEmpty()) {
                log.warn("No BEN record found in KB0D1.HDB"); //$NON-NLS-1$
                return null;
            }
            String oib = results.get(0);
            return oib != null ? oib.trim() : null;
        } catch (Exception e) {
            log.warn("Could not fetch OIB from KB0D1.HDB: {}", e.getMessage()); //$NON-NLS-1$
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Company data — AS400 KB0D1.ZBEN1
    // -------------------------------------------------------------------------

    /**
     * Fetches company name and address from KB0D1.ZBEN1.
     * Returns empty strings on failure rather than propagating the exception,
     * so the form-build flow is not blocked when the table is temporarily unavailable.
     */
    public CompanyData fetchCompanyData() {
        String sql = "SELECT TOP 1 BENNAME1, BENNAME2, BENSTR, BENPLZ, BENORT FROM " //$NON-NLS-1$
                + as400Table("KB0D1", "ZBEN1"); //$NON-NLS-1$ //$NON-NLS-2$
        log.debug("fetchCompanyData SQL: {}", sql); //$NON-NLS-1$
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
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
            log.warn("Could not fetch company data from KB0D1.ZBEN1: {}", e.getMessage()); //$NON-NLS-1$
            return CompanyData.builder().companyDescription("").companySeat("").build(); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    // -------------------------------------------------------------------------
    // MIMV_ODABRANI_TIPOVI_OBVEZNIKA — Pantheon MSSQL (local, no linked server)
    // -------------------------------------------------------------------------

    /**
     * Returns the list of active taxpayer type codes (MV01, MV02, MV03) for the given OIB.
     * Queries MIMV_ODABRANI_TIPOVI_OBVEZNIKA and collects codes whose BIT flag is 1.
     * Returns an empty list if the row does not exist or the query fails.
     */
    public List<String> fetchOdabraniTipoviObveznika(String oib) {
        String sql = "SELECT [MV01_PROIZVOĐAČ] AS mv01, MV02_TRGOVAC_NOVIM AS mv02, MV03_TRGOVAC_RABLJENIM AS mv03" //$NON-NLS-1$
                + " FROM MIMV_ODABRANI_TIPOVI_OBVEZNIKA WHERE OIB_OBVEZNIKA = ?"; //$NON-NLS-1$
        log.debug("fetchOdabraniTipoviObveznika SQL: {}", sql); //$NON-NLS-1$
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
     * Inserts vehicle detail rows into MIMV_DETALJ by executing a DB2 SELECT
     * on AS400 via the linked server and directing the result set into the local Pantheon table.
     *
     * T-SQL pattern used:
     * <pre>
     *   INSERT INTO MIMV_DETALJ (...) EXEC('DB2 SELECT ...') AT [linkedServer]
     * </pre>
     *
     * @throws IllegalStateException if no linked server is configured
     */
    public void insertMimvDetalj(String mandatorId, String oib, int formDate, String sifobr,
                                  int seqNum, int versionNum,
                                  String dateFrom, String dateTo, String companyId) {
        if (linkedServer == null || linkedServer.isBlank()) {
            throw new IllegalStateException("AS400 linked server is required for MIMV_DETALJ insert. " //$NON-NLS-1$
                    + "Configure AS400_LINKED_SERVER or _cdp_param.aclinkserver."); //$NON-NLS-1$
        }

        String db2Select = loadDetaljSelectSql(mandatorId, oib, formDate, sifobr,
                seqNum, versionNum, dateFrom, dateTo, companyId);

        String escaped = db2Select.replace("'", "''"); //$NON-NLS-1$ //$NON-NLS-2$

        String sql = "INSERT INTO " + MIMV_DETALJ //$NON-NLS-1$
                + " (OIB_OBVEZNIKA, DATUM_PP, SIFRA_OBRASCA_PP, REDNI_BROJ_PP, REDNI_BROJ_PP_PROM," //$NON-NLS-1$
                + " SIFRA_VOZILA, STATUS_VOZILA, VRSTA_VOZILA, MARKA_VOZILA, TIP_VARIJANTA_TRG_NAZIV," //$NON-NLS-1$
                + " VIN_OZNAKA, VRSTA_GORIVA, DATUM_PRVE_REGISTRACIJE, PROSJ_EMISIJA_CO2, RAZINA_EMISIJE," //$NON-NLS-1$
                + " RADNI_OBUJAM_MOTORA, SNAGA_MOTORA, PRODAJNA_CIJENA, BROJ_PRIJEDJENIH_KM," //$NON-NLS-1$
                + " KAMPER, PLUG_IN, VOZILO_71, VOZILO_81, TESTNO_VOZILO, DEPRECIJACIJA," //$NON-NLS-1$
                + " POREZNI_OBVEZNIK, OIB, BROJ_RACUNA, DATUM_IZDAVANJA_RACUNA, OBRACUNATI_IZNOS_PP)" //$NON-NLS-1$
                + " EXEC('" + escaped + "') AT [" + linkedServer + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        log.debug("insertMimvDetalj SQL (first 500 chars): {}", sql.substring(0, Math.min(500, sql.length()))); //$NON-NLS-1$
        jdbcTemplate.execute(sql);
        log.info("INSERT INTO MIMV_DETALJ completed for formDate={}, sifobr={}", formDate, sifobr); //$NON-NLS-1$
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
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads insert_mimv_detalj.sql from the classpath, strips comment lines,
     * and replaces all runtime placeholders with the provided values.
     * Returns the plain DB2 SELECT string — no EXEC AT wrapping applied here.
     */
    private String loadDetaljSelectSql(String mandatorId, String oib, int formDate, String sifobr,
                                        int seqNum, int versionNum,
                                        String dateFrom, String dateTo, String companyId) {
        try {
            byte[] bytes = getClass().getClassLoader()
                    .getResourceAsStream("sql/insert_mimv_detalj.sql").readAllBytes(); //$NON-NLS-1$
            String raw = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

            StringBuilder sb = new StringBuilder();
            for (String line : raw.split("\n")) { //$NON-NLS-1$
                if (!line.trim().startsWith("--")) { //$NON-NLS-1$
                    sb.append(line).append("\n"); //$NON-NLS-1$
                }
            }
            String sql = sb.toString();

            sql = sql.replace("<SIFPOD>",   mandatorId != null ? mandatorId : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<OIB>",      oib != null ? oib : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<DATUM>",    String.valueOf(formDate)); //$NON-NLS-1$
            sql = sql.replace("<SIFOBR>",   sifobr != null ? sifobr : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<RBR>",      String.valueOf(seqNum)); //$NON-NLS-1$
            sql = sql.replace("<RBRPROM>",  String.valueOf(versionNum)); //$NON-NLS-1$
            sql = sql.replace("<ODDATUMA>", dateFrom != null ? dateFrom : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<DODATUMA>", dateTo != null ? dateTo : ""); //$NON-NLS-1$ //$NON-NLS-2$
            sql = sql.replace("<BRANCH>",   companyId != null ? companyId : ""); //$NON-NLS-1$ //$NON-NLS-2$

            return sql.trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load sql/insert_mimv_detalj.sql", e); //$NON-NLS-1$
        }
    }

    /**
     * Extracts the human-readable description for the given taxpayer type code
     * from the packed AUKxTT columns returned by IVASDET.
     */
    private String extractDescription(String code, String[] fields) {
        for (String raw : fields) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String text = raw.trim();
            if (text.startsWith(code)) {
                int dash = text.indexOf(" - "); //$NON-NLS-1$
                if (dash >= 0) {
                    return text.substring(dash + 3).trim();
                }
            }
            int colonSpace = text.indexOf(": "); //$NON-NLS-1$
            if (colonSpace >= 0) {
                text = text.substring(colonSpace + 2).trim();
            }
            if (text.startsWith(code)) {
                int dash = text.indexOf(" - "); //$NON-NLS-1$
                if (dash >= 0) {
                    return text.substring(dash + 3).trim();
                }
            }
        }
        log.warn("No description found for taxpayer code '{}' in IVASDET AUKxTT columns", code); //$NON-NLS-1$
        return code;
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
