package com.smit.mimw.repository;

import com.smit.mimw.dto.Brand;
import com.smit.mimw.dto.TaxOffice;
import com.smit.mimw.dto.TaxPayerType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
                }
            } catch (Exception e) {
                log.warn("Could not read linked server from _cdp_param ({}). " +
                         "Falling back to direct AS400 naming.", e.getMessage());
            }
        } else {
            log.info("Using linked server from env var: '{}'", linkedServer);
        }
    }

    /**
     * Builds the fully qualified table reference.
     *
     * With linked server:   [linkedServer].[catalog].[library].[table]
     * Without linked server: library.table  (direct AS400 JDBC)
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
