package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxPayerTypesResponse {

    private List<TaxPayerType> taxPayerTypes;

    /** Settings from MIMV_POSTAVKE, shown on Ares' "Postavke" tab and used for every form. */
    private String destinationEmail;
    private String taxOfficeCode;
    private String taxOfficeDescription;
}

