package com.smit.mimw.repository;

import com.smit.mimw.dto.Brand;
import com.smit.mimw.dto.TaxOffice;
import com.smit.mimw.dto.TaxPayerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for AS400 DB2 queries used by the MI-MV module.
 *
 * Library IVASXT is global (all companies share it).
 * Library ivas0000b0 holds company-specific parameter data.
 */
@Repository
public class As400Repository {

    private static final Logger log = LoggerFactory.getLogger(As400Repository.class);

    private final JdbcTemplate jdbcTemplate;

    public As400Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------------------------
    // Tax offices (carinski uredi) — global library IVASXT
    // SELECT CIYTAA (code), CIL7AQ (description) FROM IVASXT.KLCICPP
    // -------------------------------------------------------------------------

    public List<TaxOffice> fetchTaxOffices() {
        log.debug("Querying tax offices from IVASXT.KLCICPP");
        String sql = "SELECT CIYTAA, CIL7AQ FROM IVASXT.KLCICPP";
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                TaxOffice.builder()
                        .code(rs.getString("CIYTAA") != null ? rs.getString("CIYTAA").trim() : "")
                        .description(rs.getString("CIL7AQ") != null ? rs.getString("CIL7AQ").trim() : "")
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Vehicle brands (šifarnik marki) — global library IVASXT
    // SELECT CHYSAA (code), CHL6AQ (description) FROM IVASXT.KLCHCPP
    // -------------------------------------------------------------------------

    public List<Brand> fetchBrands() {
        log.debug("Querying vehicle brands from IVASXT.KLCHCPP");
        String sql = "SELECT CHYSAA, CHL6AQ FROM IVASXT.KLCHCPP";
        return jdbcTemplate.query(sql, (rs, rowNum) ->
                Brand.builder()
                        .code(rs.getString("CHYSAA") != null ? rs.getString("CHYSAA").trim() : "")
                        .description(rs.getString("CHL6AQ") != null ? rs.getString("CHL6AQ").trim() : "")
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Taxpayer types (tipovi obveznika) — company-specific library ivas0000b0
    //
    // ivasdet stores ALL taxpayer types in a SINGLE row where:
    //   AUHHAP  = comma-separated codes       e.g. "MV01,MV02,MV03"
    //   AUK5TT  = hint text for 1st type      e.g. "Moguće vrijednosti: MV01 - PROIZVOĐAČ MOTORNIH VOZILA"
    //   AUK6TT  = hint text for 2nd type      e.g. "MV02 - TRGOVAC NOVIM MOT.VOZILIMA"
    //   AUK7TT  = hint text for 3rd type      e.g. "MV03 - REG.TRGOVAC RABLJENIM MOT.VOZ."
    //   AUK8TT  = general note (skipped)      e.g. "- unositi odvojeno zarezom"
    // -------------------------------------------------------------------------

    public List<TaxPayerType> fetchTaxPayerTypes() {
        log.debug("Querying taxpayer types from ivas0000b0.ivasdet");
        String sql = "SELECT AUHHAP, AUK5TT, AUK6TT, AUK7TT, AUK8TT " +
                     "FROM ivas0000b0.ivasdet WHERE aupgm = 'KMDPDFR'";

        return jdbcTemplate.query(sql, rs -> {
            List<TaxPayerType> result = new ArrayList<>();
            if (rs.next()) {
                // Codes: "MV01,MV02,MV03"
                String auhhap = rs.getString("AUHHAP");
                // Description hint columns — each may contain "CODE - DESCRIPTION"
                // (AUK5TT may carry a "Moguće vrijednosti: " label prefix)
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

    /**
     * Searches AUK5TT–AUK8TT columns for a line matching "CODE - description".
     * AUK5TT may carry a "Moguće vrijednosti: " label prefix which is stripped first.
     * Returns the description part, or the code itself as a fallback.
     */
    private String extractDescription(String code, String[] fields) {
        for (String raw : fields) {
            if (raw == null || raw.isBlank()) continue;
            String text = raw.trim();

            // Strip optional leading label up to ": "  (e.g. "Moguće vrijednosti: ")
            if (!text.startsWith(code)) {
                int colonSpace = text.indexOf(": ");
                if (colonSpace >= 0) {
                    text = text.substring(colonSpace + 2).trim();
                }
            }

            // Match "CODE - description"
            if (text.startsWith(code)) {
                int dash = text.indexOf(" - ");
                if (dash >= 0) {
                    return text.substring(dash + 3).trim();
                }
            }
        }
        log.warn("No description found for taxpayer code '{}' in ivasdet AUKxTT columns", code);
        return code; // fallback: return the code itself
    }
}
