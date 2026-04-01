package com.smit.mimw.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormBuildResponse {

    /** Composite identifier: oib-formDate-formTypeCode-sequentialNumber-versionNumber */
    private String id;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal taxNewVehiclesSum;
    private BigDecimal taxUsedVehiclesSum;
    private String taxPayersTypeSelected;
    private String mandatorDescription;
    private String companyDescription;
    private String companySeat;
    private String taxOfficeCode;
    private String taxOfficeDescription;
    private String destinationEmail;
    private boolean isUsed;
    private List<VehicleTaxItem> vehiclesToTax;
}
