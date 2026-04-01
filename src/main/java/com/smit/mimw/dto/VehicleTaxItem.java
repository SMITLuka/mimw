package com.smit.mimw.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Combined vehicle tax item — data from KLCGCPP (MVMZP Detalj) + KMAQCPP (MI-MV Detalj),
 * joined on vehicleCode (SifraVozila).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VehicleTaxItem {

    // --- From KLCGCPP (MVMZP Detalj) ---

    /** CGJ3AG — SifraVozila (internal Pantheon vehicle key) */
    @JsonProperty("id")
    private Integer vehicleCode;

    /** CGLUAQ — VrstaVozila (vehicle type, e.g. "M1") */
    @JsonProperty("type")
    private String vehicleType;

    /** CGYRAA — MarkaVozila code (brand code from KLCHCPP) — not exposed in API */
    private String brandCode;

    /** CGLVAQ — MarkaVozilaOpis (brand description, e.g. "Volkswagen") */
    @JsonProperty("make")
    private String brandDescription;

    /** CGLWAQ — TipVarijantaTrgNaziv (type, variant and commercial name) */
    @JsonProperty("description")
    private String commercialDescription;

    /** CGLXAQ — VinOznaka */
    private String vin;

    /** CGLYAQ — VrstaGoriva (fuel type code) */
    private String fuelType;

    /** CGI7AG — ProsjecnaEmisijaCO2 */
    @JsonProperty("co2")
    private BigDecimal co2Emission;

    /** RazinaEmisije (emission level, e.g. "VI") */
    private String emissionLevel;

    /** CGI8AG — RadniObujamMotora (engine displacement in ccm) */
    private Integer engineDisplacement;

    /** BrPotvrdeSukladnosti (compliance certificate number) */
    private String complianceCertificateNumber;

    /** CGI9AG — PoreznaOsnovica (tax base in HRK/EUR) */
    private BigDecimal taxBase;

    /** CGJAAG — PoreznaStopa (tax rate %) */
    private BigDecimal taxRate;

    /** CGJBAG — IznosPP (special tax amount) */
    private BigDecimal specialTaxAmount;

    /** Oslobodenje (exemption code/description) */
    private String exemption;

    /** CGJDAG — PlugIn (plug-in hybrid percentage) */
    private BigDecimal plugIn;

    /** Kamper (camper percentage/code) */
    private String camper;

    /** PorezniObveznik (tax payer / buyer name) */
    private String taxPayer;

    /** OIB (buyer's OIB, 11 digits) */
    private String taxPayerOib;

    /** BrRacuna (invoice number) */
    private String invoiceNumber;

    /** CGJEAG — DatumIzdavanjaRacuna (invoice date, YYYYMMDD) */
    private Integer invoiceDate;

    /** CGJFAG — IznosUplacenogPP (paid special tax amount) */
    private BigDecimal paidTaxAmount;

    /** CGJGAG — DatumUplate (payment date, YYYYMMDD) */
    private Integer paymentDate;

    // --- From KMAQCPP (MI-MV Detalj) ---

    /** AQQWAQ — StatusVozila (NEW=new, USED=used) */
    private String status;

    /** AQSBAG — DatumPrveRegistracije (first registration date) */
    private LocalDate dateFirstRegistration;

    /** AQSCAG — SnagaMotora (engine power in kW) */
    private BigDecimal enginePower;

    /** AQSDAG — ProdajnaCijena (selling price) */
    private BigDecimal sellingPrice;

    /** AQSEAG — BrojPrijedjenihKM (mileage in km) */
    private BigDecimal mileage;

    /** AQQXAQ — Vozilo71 (7+1 vehicle flag) */
    private String vehicle71;

    /** AQQYAQ — Vozilo81 (8+1 vehicle flag) */
    private String vehicle81;

    /** AQSFAG — TestnoVozilo (test vehicle percentage) */
    private BigDecimal testVehicle;

    /** AQSGAG — Deprecijacija (depreciation percentage) */
    private BigDecimal depreciation;

    /** AQSHAG — ObracunatiIznosPP (calculated special tax amount) */
    private BigDecimal calculatedTaxAmount;
}
