package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body of POST /mimv/settings — everything on Ares' "Postavke" tab:
 * selected taxpayer types, destination e-mail and customs office.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsRequest {

    private List<TaxPayerTypeRequest> taxPayerTypes;
    private String destinationEmail;
    private String taxOfficeCode;
    private String taxOfficeDescription;
}
