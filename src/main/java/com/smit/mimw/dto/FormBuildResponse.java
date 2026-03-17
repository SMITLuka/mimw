package com.smit.mimw.dto;

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
public class FormBuildResponse {

    private Long id;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal taxNewVehiclesSum;
    private BigDecimal taxUsedVehiclesSum;
    private String taxPayersTypeSelected;
    private String mandatorDescription;
    private String companyDescription;
    private String taxOfficeCode;
    private String taxOfficeDescription;
    private String destinationEmail;
    private boolean isUsed;
    private List<VehicleTaxItem> vehiclesToTax;
}

