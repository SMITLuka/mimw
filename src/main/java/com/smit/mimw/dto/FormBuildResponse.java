package com.smit.mimw.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for POST /mimv/form/build.
 * Contains the inserted MIMV_ZAGLAVLJE header and all MIMV_DETALJ rows for the period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormBuildResponse {

    /** The inserted MIMV_ZAGLAVLJE header row. */
    private MimvZaglavljeItem zaglavlje;

    /** All MIMV_DETALJ rows inserted for this form. */
    private List<MimvDetaljItem> detalji;
}
