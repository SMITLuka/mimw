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
public class VehicleTaxItem {

    private Long id;
    private String status;
    private String type;
    private String make;
    private String description;
    private String vin;
    private String fuelType;
    private LocalDate dateFirstRegistration;
    private Double co2;
}

