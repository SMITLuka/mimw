package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxPayerTypeRequest {

    private String taxPayerCode;
    private String taxPayerDescription;
    /** Whether this taxpayer type is selected. */
    private boolean selected;

}
