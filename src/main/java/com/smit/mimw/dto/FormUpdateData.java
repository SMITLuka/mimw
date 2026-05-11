package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload for POST /mimv/form/build/update.
 *
 * Form key: taxPayerCode + formDate + sequentialNumberInPeriod + versionNumber identify
 * the MIMV_ZAGLAVLJE / MIMV_DETALJ rows to overwrite. OIB is fetched from AS400 transparently.
 *
 * zaglavlje carries only the mutable display fields (no key, no totals).
 * detalji is the full replacement list — existing rows are deleted and re-inserted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormUpdateData
{
    /**
     * TIPOVI_OBVEZNIKA — MV01, MV02, or MV03.
     * Resolves to SIFRA_OBRASCA_PP (MV02 -> 401, MV03 -> 405) and is part of the form key.
     */
    private String taxPayerCode;

    /** DATUM_PP as YYYYMMDD integer — form key (e.g. 20260101). */
    private Integer formDate;

    /** REDNI_BROJ_PP — sequential number in the period. Defaults to 1 when null. */
    private Integer sequentialNumberInPeriod;

    /** REDNI_BROJ_PP_PROM — version/amendment number. Defaults to 1 when null. */
    private Integer versionNumber;

    /** Mutable display fields for MIMV_ZAGLAVLJE (excluding key fields and computed totals). */
    private ZaglavljeUpdateData zaglavlje;

    /** Full replacement list of vehicle detail rows for MIMV_DETALJ. */
    private List<MimvDetaljItem> detalji;
}
