package com.smit.mimw.repository;

import com.smit.mimw.dto.Brand;
import com.smit.mimw.dto.KlcfcppRecord;
import com.smit.mimw.dto.TaxOffice;
import com.smit.mimw.dto.TaxPayerType;
import com.smit.mimw.dto.VehicleTaxItem;
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

/**
 * Repository for AS400 DB2 queries used by the MI-MV module.
 *
 * When connected via SQL Server (Render deployment):
 *   Queries go through a Linked Server, exactly as Pantheon does.
 *   Table format: [linkedServer].[catalog].[library].[table]
 *   e.g.          [AS400_LS].[ADRIAVC1].[IVASXT].[KLCICPP]
 *
 * When connected directly to AS400 (local dev, linkedServer blank):
 *   Table format: library.table  (e.g. IVASXT.KLCICPP)
 */
@Repository
public class As400Repository {

    private static final Logger log = LoggerFactory.getLogger(As400Repository.class);

    private final JdbcTemplate jdbcTemplate;

    /** Linked server name — set via env var AS400_LINKED_SERVER,
     *  or auto-read from _cdp_param.aclinkserver at startup. */
    @Value("${as400.linked-server:}")
    private String linkedServer;

    /** AS400 catalog/database name — default ADRIAVC1 */
    @Value("${as400.catalog:ADRIAVC1}")
    private String catalog;

    public As400Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * If linkedServer was not provided via env var, try to read it from
     * _cdp_param table — the same table Pantheon reads on startup.
     */
    @PostConstruct
    public void init() {
        if (linkedServer == null || linkedServer.isBlank()) {
            try {
                String ls = jdbcTemplate.queryForObject(
                        "SELECT TOP 1 aclinkserver FROM _cdp_param", String.class);
                if (ls != null && !ls.isBlank()) {
                    linkedServer = ls.trim();
                    log.info("Loaded linked server name from _cdp_param: '{}'", linkedServer);
                } else {
                    log.error("_cdp_param.aclinkserver is empty. " +
                              "AS400 is only reachable via Pantheon SQL Server linked server. " +
                              "Set env var AS400_LINKED_SERVER or populate _cdp_param.aclinkserver.");
                }
            } catch (Exception e) {
                log.error("Could not read linked server from _cdp_param ({}). " +
                          "AS400 is only reachable via Pantheon SQL Server linked server. " +
                          "Set env var AS400_LINKED_SERVER.", e.getMessage());
            }
        } else {
            log.info("Using linked server from env var: '{}'", linkedServer);
        }
    }

    /**
     * Builds the fully qualified table reference.
     *
     * Connection is always to Pantheon SQL Server; AS400 is accessed via linked server.
     *
     * With linked server:    [linkedServer].[catalog].[library].[table]
     * Without linked server: library.table  (fallback — AS400 unreachable without linked server)
     */
    private String table(String library, String table) {
        if (linkedServer == null || linkedServer.isBlank()) {
            return library + "." + table;
        }
        return "[" + linkedServer + "].[" + catalog + "].[" + library + "].[" + table + "]";
    }

    // -------------------------------------------------------------------------
    // Tax offices (carinski uredi) — library IVASXT, table KLCICPP
    // -------------------------------------------------------------------------

    public List<TaxOffice> fetchTaxOffices() {
        String sql = "SELECT CIYTAA, CIL7AQ FROM " + table("IVASXT", "KLCICPP");
        log.debug("fetchTaxOffices SQL: {}", sql);
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                TaxOffice.builder()
                        .code(rs.getString("CIYTAA") != null ? rs.getString("CIYTAA").trim() : "")
                        .description(rs.getString("CIL7AQ") != null ? rs.getString("CIL7AQ").trim() : "")
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Vehicle brands (šifarnik marki) — library IVASXT, table KLCHCPP
    // -------------------------------------------------------------------------

    public List<Brand> fetchBrands() {
        String sql = "SELECT CHYSAA, CHL6AQ FROM " + table("IVASXT", "KLCHCPP");
        log.debug("fetchBrands SQL: {}", sql);
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                Brand.builder()
                        .code(rs.getString("CHYSAA") != null ? rs.getString("CHYSAA").trim() : "")
                        .description(rs.getString("CHL6AQ") != null ? rs.getString("CHL6AQ").trim() : "")
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Taxpayer types (tipovi obveznika) — library IVAS0000B0, table IVASDET
    // -------------------------------------------------------------------------

    public List<TaxPayerType> fetchTaxPayerTypes() {
        String sql = "SELECT TOP 1 AUHHAP, AUK5TT, AUK6TT, AUK7TT, AUK8TT FROM "
                + table("IVAS0000B0", "IVASDET")
                + " WHERE aupgm = 'KMDPDFR'";
        log.debug("fetchTaxPayerTypes SQL: {}", sql);

        return jdbcTemplate.query(sql, rs -> {
            List<TaxPayerType> result = new ArrayList<>();
            if (rs.next()) {
                String auhhap = rs.getString("AUHHAP");
                String[] descColumns = {
                    rs.getString("AUK5TT"),
                    rs.getString("AUK6TT"),
                    rs.getString("AUK7TT"),
                    rs.getString("AUK8TT")
                };

                if (auhhap != null && !auhhap.isBlank()) {
                    for (String raw : auhhap.trim().split(",")) {
                        String code = raw.trim();
                        if (code.isEmpty()) continue;
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
    // KLCFCPP — MI-MV form headers (MVMZP Zaglavlje) — library IVAS0000B0
    // -------------------------------------------------------------------------

    /**
     * Fetches rows from IVAS0000B0.KLCFCPP.
     *
     * @param companyCode  CFNSRO value (6-char company code, e.g. "000080"). Pass null to skip filter.
     * @param dateFrom     filter: CFI3AG (RazdobljeOd) >= dateFrom (YYYYMMDD). Pass null to skip.
     * @param dateTo       filter: CFI4AG (RazdobljeDo) <= dateTo (YYYYMMDD). Pass null to skip.
     */
    public List<KlcfcppRecord> fetchKlcfcpp(String companyCode, LocalDate dateFrom, LocalDate dateTo) {
        StringBuilder sql = new StringBuilder(
                "SELECT CFYQAA, CFYPAA, CFRIDX, CFNSRO, CFMSTS, CFLTAQ, CFLSAQ, CFLRAQ," +
                " CFLQAQ, CFLPAQ, CFLOAQ, CFLNAQ, CFLMAQ, CFKRAQ, CFKQAQ," +
                " CFIZAU, CFIYAU, CFI6AG, CFI5AG, CFI4AG, CFI3AG, CFI2AG, CFI1AG, CFI0AG," +
                " CFFSDG, CFFRDG, CFB4SB FROM ");
        sql.append(table("IVAS0000B0", "KLCFCPP"));

        List<Object> params = new ArrayList<>();

        if (companyCode != null && !companyCode.isBlank()) {
            sql.append(" WHERE CFNSRO = ?");
            params.add(companyCode);
        }
        if (dateFrom != null) {
            sql.append(params.isEmpty() ? " WHERE" : " AND");
            sql.append(" CFI3AG >= ?");
            params.add(Integer.parseInt(dateFrom.format(DateTimeFormatter.ofPattern("yyyyMMdd"))));
        }
        if (dateTo != null) {
            sql.append(params.isEmpty() ? " WHERE" : " AND");
            sql.append(" CFI4AG <= ?");
            params.add(Integer.parseInt(dateTo.format(DateTimeFormatter.ofPattern("yyyyMMdd"))));
        }

        log.debug("fetchKlcfcpp SQL: {}", sql);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) ->
                KlcfcppRecord.builder()
                        .sifraObrascaPP(trim(rs.getString("CFYQAA")))
                        .oibObveznika(trim(rs.getString("CFYPAA")))
                        .sifraKorisnika(trim(rs.getString("CFRIDX")))
                        .sifraPoduzecea(trim(rs.getString("CFNSRO")))
                        .statusSloga(trim(rs.getString("CFMSTS")))
                        .carinskiUredOpis(trim(rs.getString("CFLTAQ")))
                        .identifikator(trim(rs.getString("CFLSAQ")))
                        .akcijaPP(trim(rs.getString("CFLRAQ")))
                        .emailAdresa(trim(rs.getString("CFLQAQ")))
                        .odgovornaOsoba(trim(rs.getString("CFLPAQ")))
                        .sjedisteObveznika(trim(rs.getString("CFLOAQ")))
                        .nazivObveznika(trim(rs.getString("CFLNAQ")))
                        .carinskiUred(trim(rs.getString("CFLMAQ")))
                        .tekstDodatni2(trim(rs.getString("CFKRAQ")))
                        .tekstDodatni1(trim(rs.getString("CFKQAQ")))
                        .iznosDodatni2(rs.getBigDecimal("CFIZAU"))
                        .iznosDodatni1(rs.getBigDecimal("CFIYAU"))
                        .ukIznosUplacenogPP(rs.getBigDecimal("CFI6AG"))
                        .ukIznosPP(rs.getBigDecimal("CFI5AG"))
                        .razdobljeDo(nullableInt(rs, "CFI4AG"))
                        .razdobljeOd(nullableInt(rs, "CFI3AG"))
                        .redniBrojPPPromjena(nullableInt(rs, "CFI2AG"))
                        .redniBrojPP(nullableInt(rs, "CFI1AG"))
                        .datumPP(nullableInt(rs, "CFI0AG"))
                        .datumDodatni2(rs.getBigDecimal("CFFSDG"))
                        .datumDodatni1(rs.getBigDecimal("CFFRDG"))
                        .statusSloga2(trim(rs.getString("CFB4SB")))
                        .build(),
                params.toArray()
        );
    }

    // -------------------------------------------------------------------------
    // OIB — from KB0D1.HDB where HDBSART = 'BEN'
    // OIB is stored at fixed position 324, length 11, inside the HDBINHALT field.
    // -------------------------------------------------------------------------

    /**
     * Fetches the taxpayer OIB from {@code KB0D1.HDB}.
     * <p>
     * The {@code HDBINHALT} column is a packed fixed-width text block.
     * OIB occupies bytes 324–334 (1-based, length 11):
     * <pre>
     *   name1(30) + name2(30) + street(30) + country+zip(13) + city(21)
     *   + phone1(20) + phone2(20) + fax(20) + bank(30) + bankstreet(30)
     *   + iban(35) + blank(16) + pp(20) + blank(8)  →  offset 324
     * </pre>
     *
     * @return trimmed 11-digit OIB string, or {@code null} if not found / on error
     */
    public String fetchOib() {
        // OIB is at fixed offset 324, length 11 inside the HDBINHALT field.
        // fields before OIB (each padded to fixed length):
        // name1(30) + name2(30) + street(30) + country+zip(13) + city(21)
        // + phone1(20) + phone2(20) + fax(20) + bank(30) + bankstreet(30)
        // + iban(35) + blank(16) + pp(20) + blank(8)  →  offset 324, length 11
        //
        // NOTE: query goes through SQL Server 4-part linked-server name,
        // so SQL Server syntax must be used: SUBSTRING (not DB2 substr).
        String sql = "SELECT TRIM(SUBSTRING(HDBINHALT, 324, 11)) AS OIB FROM "
                + table("KB0D1", "HDB") + " WHERE HDBSART = 'BEN'";
        log.debug("fetchOib SQL: {}", sql);
        try {
            List<String> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("OIB"));
            if (results.isEmpty()) {
                log.warn("No BEN record found in KB0D1.HDB");
                return null;
            }
            String oib = results.get(0);
            return oib != null ? oib.trim() : null;
        } catch (Exception e) {
            log.warn("Could not fetch OIB from KB0D1.HDB: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // INSERT INTO KLCGCPP — MVMZP Detalj (sql/insert_klcgcpp.sql)
    // -------------------------------------------------------------------------

    /**
     * Executes the insert into IVAS&lt;mandatorId&gt;.KLCGCPP by loading
     * {@code sql/insert_klcgcpp.sql} and replacing placeholders with real values.
     */
    public void executeInsertKlcgcpp(String mandatorId, String oib, String formDate,
                                     String formTypeCode, int seqNum, int versionNum,
                                     String dateFrom, String dateTo, String companyId) {
        String sql = loadAndReplacePlaceholders("sql/insert_klcgcpp.sql",
                mandatorId, oib, formDate, formTypeCode, seqNum, versionNum, dateFrom, dateTo, companyId);
        log.debug("executeInsertKlcgcpp SQL (first 500 chars): {}", sql.substring(0, Math.min(500, sql.length())));
        jdbcTemplate.execute(sql);
        log.info("INSERT INTO KLCGCPP completed for mandator={}, oib={}", mandatorId, oib != null ? "***" : "null");
    }

    // -------------------------------------------------------------------------
    // INSERT INTO KMAQCPP — MI-MV Detalj (sql/insert_kmaqcpp.sql)
    // -------------------------------------------------------------------------

    /**
     * Executes the insert into IVAS&lt;mandatorId&gt;.KMAQCPP by loading
     * {@code sql/insert_kmaqcpp.sql} and replacing placeholders with real values.
     */
    public void executeInsertKmaqcpp(String mandatorId, String oib, String formDate,
                                     String formTypeCode, int seqNum, int versionNum,
                                     String dateFrom, String dateTo, String companyId) {
        String sql = loadAndReplacePlaceholders("sql/insert_kmaqcpp.sql",
                mandatorId, oib, formDate, formTypeCode, seqNum, versionNum, dateFrom, dateTo, companyId);
        log.debug("executeInsertKmaqcpp SQL (first 500 chars): {}", sql.substring(0, Math.min(500, sql.length())));
        jdbcTemplate.execute(sql);
        log.info("INSERT INTO KMAQCPP completed for mandator={}, oib={}", mandatorId, oib != null ? "***" : "null");
    }

    // -------------------------------------------------------------------------
    // SELECT vehicles from KLCGCPP + KMAQCPP (joined on SifraVozila)
    // -------------------------------------------------------------------------

    /**
     * Fetches vehicle tax items by joining KLCGCPP (MVMZP Detalj) and KMAQCPP (MI-MV Detalj)
     * on the common key columns (company, OIB, formDate, formTypeCode, seqNum, versionNum, vehicleCode).
     */
    public List<VehicleTaxItem> fetchVehicleTaxItems(String mandatorId, String oib, String formDate,
                                                     String formTypeCode, int seqNum, int versionNum) {
        String klcg = table("IVAS" + mandatorId, "KLCGCPP");
        String kmaq = table("IVAS" + mandatorId, "KMAQCPP");

        String sql = "SELECT " +
                // KLCGCPP columns
                "g.CGJ3AG, g.CGLUAQ, g.CGYRAA, g.CGLVAQ, g.CGLWAQ, g.CGLXAQ, g.CGLYAQ, " +
                "g.CGI7AG, g.CGL2AQ, g.CGI8AG, g.CGL0AQ, g.CGI9AG, g.CGJAAG, g.CGJBAG, " +
                "g.CGL1AQ, g.CGJDAG, g.CGL2AQ AS CAMPER_AQ, g.CGL3AQ, g.CGL4AQ, g.CGL5AQ, " +
                "g.CGJEAG, g.CGJFAG, g.CGJGAG, " +
                // KMAQCPP columns
                "q.AQQWAQ, q.AQSBAG, q.AQSCAG, q.AQSDAG, q.AQSEAG, " +
                "q.AQQXAQ, q.AQQYAQ, q.AQSFAG, q.AQSGAG, q.AQSHAG " +
                "FROM " + klcg + " g " +
                "LEFT JOIN " + kmaq + " q ON " +
                "  g.CGNSRO = q.AQNSRO AND g.CGYPAA = q.AQYPAA AND g.CGI0AG = q.AQI0AG " +
                "  AND g.CGYQAA = q.AQYQAA AND g.CGI1AG = q.AQI1AG AND g.CGI2AG = q.AQI2AG " +
                "  AND g.CGJ3AG = q.AQJ3AG " +
                "WHERE g.CGYPAA = ? AND g.CGI0AG = ? AND g.CGYQAA = ? AND g.CGI1AG = ? AND g.CGI2AG = ?";

        log.debug("fetchVehicleTaxItems SQL: {}", sql);

        return jdbcTemplate.query(sql,
                new Object[]{oib, Integer.parseInt(formDate), formTypeCode, seqNum, versionNum},
                (rs, rowNum) -> VehicleTaxItem.builder()
                        // KLCGCPP fields
                        .vehicleCode(nullableInt(rs, "CGJ3AG"))
                        .vehicleType(trim(rs.getString("CGLUAQ")))
                        .brandCode(trim(rs.getString("CGYRAA")))
                        .brandDescription(trim(rs.getString("CGLVAQ")))
                        .commercialDescription(trim(rs.getString("CGLWAQ")))
                        .vin(trim(rs.getString("CGLXAQ")))
                        .fuelType(trim(rs.getString("CGLYAQ")))
                        .co2Emission(rs.getBigDecimal("CGI7AG"))
                        .emissionLevel(trim(rs.getString("CGL2AQ")))
                        .engineDisplacement(nullableInt(rs, "CGI8AG"))
                        .complianceCertificateNumber(trim(rs.getString("CGL0AQ")))
                        .taxBase(rs.getBigDecimal("CGI9AG"))
                        .taxRate(rs.getBigDecimal("CGJAAG"))
                        .specialTaxAmount(rs.getBigDecimal("CGJBAG"))
                        .exemption(trim(rs.getString("CGL1AQ")))
                        .plugIn(rs.getBigDecimal("CGJDAG"))
                        .camper(trim(rs.getString("CAMPER_AQ")))
                        .taxPayer(trim(rs.getString("CGL3AQ")))
                        .taxPayerOib(trim(rs.getString("CGL4AQ")))
                        .invoiceNumber(trim(rs.getString("CGL5AQ")))
                        .invoiceDate(nullableInt(rs, "CGJEAG"))
                        .paidTaxAmount(rs.getBigDecimal("CGJFAG"))
                        .paymentDate(nullableInt(rs, "CGJGAG"))
                        // KMAQCPP fields
                        .status(trim(rs.getString("AQQWAQ")))
                        .dateFirstRegistration(rs.getBigDecimal("AQSBAG"))
                        .enginePower(rs.getBigDecimal("AQSCAG"))
                        .sellingPrice(rs.getBigDecimal("AQSDAG"))
                        .mileage(rs.getBigDecimal("AQSEAG"))
                        .vehicle71(trim(rs.getString("AQQXAQ")))
                        .vehicle81(trim(rs.getString("AQQYAQ")))
                        .testVehicle(rs.getBigDecimal("AQSFAG"))
                        .depreciation(rs.getBigDecimal("AQSGAG"))
                        .calculatedTaxAmount(rs.getBigDecimal("AQSHAG"))
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Helper: load SQL from classpath resource and replace placeholders
    // -------------------------------------------------------------------------

    /**
     * Loads a SQL file from the classpath, strips comment lines, replaces
     * the 9 standard placeholders with the provided values, and — when a
     * SQL Server linked server is configured — wraps the result in a
     * pass-through {@code EXEC('...') AT [linkedServer]} call so that
     * SQL Server does NOT parse the DB2-native syntax
     * ({@code ifnull}, {@code isnumericdec}, {@code datefmt},
     * {@code sysibm.sysdummy1}, etc.).
     */
    private String loadAndReplacePlaceholders(String resourcePath,
                                              String mandatorId, String oib, String formDate,
                                              String formTypeCode, int seqNum, int versionNum,
                                              String dateFrom, String dateTo, String companyId) {
        try {
            String raw = new String(
                    getClass().getClassLoader().getResourceAsStream(resourcePath).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);

            // Strip SQL comment lines (lines starting with --)
            StringBuilder sb = new StringBuilder();
            for (String line : raw.split("\n")) {
                if (!line.trim().startsWith("--")) {
                    sb.append(line).append("\n");
                }
            }
            String sql = sb.toString();

            sql = sql.replace("<SIFPOD>",   mandatorId != null ? mandatorId : "");
            sql = sql.replace("<OIB>",      oib != null ? oib : "");
            sql = sql.replace("<DATUM>",    formDate);
            sql = sql.replace("<SIFOBR>",   formTypeCode != null ? formTypeCode : "");
            sql = sql.replace("<RBR>",      String.valueOf(seqNum));
            sql = sql.replace("<RBRPROM>",  String.valueOf(versionNum));
            sql = sql.replace("<ODDATUMA>", dateFrom);
            sql = sql.replace("<DODATUMA>", dateTo);
            sql = sql.replace("<BRANCH>",   companyId != null ? companyId : "");

            sql = sql.trim();

            if (linkedServer != null && !linkedServer.isBlank()) {
                // ----------------------------------------------------------------
                // SQL Server linked-server path:
                // The INSERT SQL is DB2-native and contains functions that SQL
                // Server does not recognise (ifnull, isnumericdec, datefmt, …).
                // Wrapping with EXEC('…') AT [linkedServer] sends the string
                // directly to AS400 for execution, bypassing SQL Server's parser.
                //
                // Single quotes inside the SQL must be doubled so the outer
                // EXEC string literal is valid T-SQL.
                // ----------------------------------------------------------------
                String escaped = sql.replace("'", "''");
                String passThrough = "EXEC('" + escaped + "') AT [" + linkedServer + "]";
                log.debug("Using pass-through EXEC ... AT [{}] for {}", linkedServer, resourcePath);
                return passThrough;
            }

            // Direct AS400/DB2 JDBC path — send DB2-native SQL as-is.
            return sql;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load SQL resource: " + resourcePath, e);
        }
    }

    private static String trim(String s) {
        return s != null ? s.trim() : null;
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private String extractDescription(String code, String[] fields) {
        for (String raw : fields) {
            if (raw == null || raw.isBlank()) continue;
            String text = raw.trim();
            if (!text.startsWith(code)) {
                int colonSpace = text.indexOf(": ");
                if (colonSpace >= 0) text = text.substring(colonSpace + 2).trim();
            }
            if (text.startsWith(code)) {
                int dash = text.indexOf(" - ");
                if (dash >= 0) return text.substring(dash + 3).trim();
            }
        }
        log.warn("No description found for taxpayer code '{}' in ivasdet AUKxTT columns", code);
        return code;
    }
}
