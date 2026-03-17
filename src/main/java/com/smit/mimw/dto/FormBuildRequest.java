package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormBuildRequest {

    private String taxPayerCode;
    private LocalDate formDate;
    private Integer sequentialNumberInPeriod;
    private Integer versionNumber;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String taxOfficeCode;
    private String taxOfficeDescription;
    private String destinationEmail;
}

