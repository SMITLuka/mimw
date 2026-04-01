package com.smit.mimw.service;

import com.smit.mimw.dto.*;
import com.smit.mimw.repository.As400Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for taxpayer type and form-build operations.
 * <p>
 * Three methods are backed by live AS400 DB2 queries (via As400Repository).
 * The remaining methods still return dummy data — replace with AS400 calls when ready.
 */
@Service
public class TaxPayerService {

    private static final Logger log = LoggerFactory.getLogger(TaxPayerService.class);

    /**
     * Set to {@code true} to return hardcoded demo data without touching AS400.
     * Set to {@code false} to run the real insert + select flow against live data.
     */
    private static final boolean DEMO_MODE = true;

    /**
     * Maps Pantheon taxpayer-type codes to their numeric MI-MV form-type codes (SifraObrascaPP).
     * <ul>
     *   <li>MV02 -&gt; 401 — passenger car / motorcycle dealers (HFSFZGART: N, V)</li>
     *   <li>MV03 -&gt; 405 — quad / ATV dealers (HFSFZGART: A, G)</li>
     * </ul>
     */
    private static final Map<String, String> SIFOBR_BY_TAX_CODE = Map.of(
            "MV02", "401",
            "MV03", "405"
    );

    /** Returns the numeric SifraObrascaPP for the given taxPayerCode, or {@code null} if unknown. */
    private static String toSifobr(String taxPayerCode) {
        if (taxPayerCode == null) return null;
        return SIFOBR_BY_TAX_CODE.get(taxPayerCode.toUpperCase());
    }

    private final As400Repository as400Repository;

    public TaxPayerService(As400Repository as400Repository) {
        this.as400Repository = as400Repository;
    }

    /**
     * Returns a list of taxpayer types from AS400 DB2.
     * <p>
     * Query: SELECT AUVARA, AUOPIS FROM ivas0000b0.ivasdet WHERE aupgm = 'KMDPDFR'
     * <p>
     * TODO: Verify column names AUVARA (code) and AUOPIS (description) against the actual IVASDET DDL.
     */
    public TaxPayerTypesResponse getTaxPayerTypes(String mandatorId, String companyId) {
        log.info("Fetching taxpayer types from AS400 for mandatorId={}, companyId={}", mandatorId, companyId);
        List<TaxPayerType> types = as400Repository.fetchTaxPayerTypes();
        return TaxPayerTypesResponse.builder()
                .taxPayerTypes(types)
                .build();
    }

    /**
     * Saves a taxpayer type (dummy implementation).
     * <p>
     * TODO: Replace with AS400 DB2 insert, e.g.:
     *   jdbcTemplate.update("INSERT INTO TAX_PAYER_TYPES (TAX_CODE, TAX_DESC, MANDATOR_ID, COMPANY_ID) VALUES (?,?,?,?)", ...)
     */
    public IsSuccessResponse saveTaxPayerType(String mandatorId, String companyId, List<TaxPayerTypeRequest> requests) {
        log.info("Saving {} taxpayer type(s) for mandatorId={}, companyId={}",
                requests.size(), mandatorId, companyId);

        // TODO: Replace with AS400 DB2 insert for each entry
        for (TaxPayerTypeRequest request : requests) {
            if (request.getTaxPayerCode() == null || request.getTaxPayerCode().isBlank()) {
                throw new IllegalArgumentException("taxPayerCode is required.");
            }
            if (request.getTaxPayerDescription() == null || request.getTaxPayerDescription().isBlank()) {
                throw new IllegalArgumentException("taxPayerDescription is required.");
            }
            log.info("  -> code={}, description={}", request.getTaxPayerCode(), request.getTaxPayerDescription());
        }

        // Dummy: always succeeds
        return IsSuccessResponse.builder()
                .isSuccess(true)
                .build();
    }

    /**
     * Builds a MI-MV form:
     * <ol>
     *   <li>Resolve placeholders: OIB (from KB0D1.HDB), SIFOBR (from taxPayerCode mapping)</li>
     *   <li>Execute INSERT INTO KLCGCPP (MVMZP Detalj) — replace SQL placeholders with real values</li>
     *   <li>Execute INSERT INTO KMAQCPP (MI-MV Detalj) — replace SQL placeholders with real values</li>
     *   <li>SELECT from KLCFCPP (zaglavlje) to fill response header</li>
     *   <li>SELECT from KLCGCPP + KMAQCPP (joined on vehicleCode) to fill vehiclesToTax</li>
     * </ol>
     *
     * Composite ID format: {@code oib-formDate-formTypeCode-sequentialNumber-versionNumber}
     * <br>Example: {@code 30985203273-01082014-405-01-001}
     */
    public FormBuildResponse buildForm(String mandatorId, String companyId, FormBuildRequest request) {
        log.info("Building form for mandatorId={}, companyId={}, taxPayerCode={}, dateFrom={}, dateTo={}",
                mandatorId, companyId, request.getTaxPayerCode(), request.getDateFrom(), request.getDateTo());

        // ---------------------------------------------------------------
        // DEMO MODE — set DEMO_MODE = false when connecting to live AS400
        // ---------------------------------------------------------------
        if (DEMO_MODE) {
            return FormBuildResponse.builder()
                    .id("30985203273-01032026-401-01-001")
                    .dateFrom(LocalDate.of(2026, 3, 1))
                    .dateTo(LocalDate.of(2026, 3, 31))
                    .taxNewVehiclesSum(new BigDecimal("4500.00"))
                    .taxUsedVehiclesSum(new BigDecimal("2100.00"))
                    .taxPayersTypeSelected("01")
                    .mandatorDescription("Mandator B9")
                    .companyDescription("Company 001")
                    .taxOfficeCode(null)
                    .taxOfficeDescription(null)
                    .destinationEmail(null)
                    .isUsed(false)
                    .vehiclesToTax(List.of(
                            VehicleTaxItem.builder()
                                    .vehicleCode(1)
                                    .status("NEW")
                                    .vehicleType("M1")
                                    .brandDescription("Volkswagen")
                                    .commercialDescription("Golf 8 Style 1.5 TSI, automatic, silver metallic")
                                    .vin("WVWZZZ1KZMP012345")
                                    .fuelType("Petrol")
                                    .dateFirstRegistration(LocalDate.of(2026, 1, 15))
                                    .co2Emission(new BigDecimal("126.0"))
                                    .build(),
                            VehicleTaxItem.builder()
                                    .vehicleCode(2)
                                    .status("USED")
                                    .vehicleType("M1")
                                    .brandDescription("BMW")
                                    .commercialDescription("320d xDrive, automatic, black")
                                    .vin("WBA8E1C05JA987654")
                                    .fuelType("Diesel")
                                    .dateFirstRegistration(LocalDate.of(2022, 6, 10))
                                    .co2Emission(new BigDecimal("134.0"))
                                    .build(),
                            VehicleTaxItem.builder()
                                    .vehicleCode(3)
                                    .status("NEW")
                                    .vehicleType("M1")
                                    .brandDescription("Audi")
                                    .commercialDescription("A4 40 TFSI S-tronic, grey metallic")
                                    .vin("WAUZZZ8K9NA012789")
                                    .fuelType("Petrol")
                                    .dateFirstRegistration(LocalDate.of(2026, 2, 3))
                                    .co2Emission(new BigDecimal("142.0"))
                                    .build(),
                            VehicleTaxItem.builder()
                                    .vehicleCode(4)
                                    .status("USED")
                                    .vehicleType("M1")
                                    .brandDescription("Toyota")
                                    .commercialDescription("Yaris Cross 1.5 Hybrid, white")
                                    .vin("NMTK33BV80R123456")
                                    .fuelType("Hybrid")
                                    .dateFirstRegistration(LocalDate.of(2021, 11, 22))
                                    .co2Emission(new BigDecimal("92.0"))
                                    .build(),
                            VehicleTaxItem.builder()
                                    .vehicleCode(5)
                                    .status("NEW")
                                    .vehicleType("M1")
                                    .brandDescription("Mercedes-Benz")
                                    .commercialDescription("C 220 d 4MATIC, obsidian black metallic")
                                    .vin("WDD2050421A567890")
                                    .fuelType("Diesel")
                                    .dateFirstRegistration(LocalDate.of(2026, 3, 1))
                                    .co2Emission(new BigDecimal("118.0"))
                                    .build()
                    ))
                    .build();
        }
        // ---------------------------------------------------------------
        // END DEMO MODE
        // ---------------------------------------------------------------

        // --- 1. Resolve placeholder values ---

        String oib = as400Repository.fetchOib();
        log.info("Fetched OIB from KB0D1.HDB: {}", oib != null ? "***" : "null");

        String formTypeCode = toSifobr(request.getTaxPayerCode());
        log.info("Resolved formTypeCode '{}' from taxPayerCode '{}'", formTypeCode, request.getTaxPayerCode());

        // Pad companyId to 6 chars if numeric (DB stores "000080" for company 80)
        String companyCode = companyId;
        if (companyId != null && companyId.matches("\\d+")) {
            companyCode = String.format("%06d", Long.parseLong(companyId));
        }

        DateTimeFormatter yyyyMMdd = DateTimeFormatter.ofPattern("yyyyMMdd");
        String formDateStr   = request.getFormDate() != null ? request.getFormDate().format(yyyyMMdd) : "";
        String dateFromStr   = request.getDateFrom() != null ? request.getDateFrom().format(yyyyMMdd) : "";
        String dateToStr     = request.getDateTo()   != null ? request.getDateTo().format(yyyyMMdd)   : "";
        int seqNum           = request.getSequentialNumberInPeriod() != null ? request.getSequentialNumberInPeriod() : 1;
        int versionNum       = request.getVersionNumber() != null ? request.getVersionNumber() : 1;

        // --- 2. Execute INSERT INTO KLCGCPP (MVMZP Detalj) ---
        as400Repository.executeInsertKlcgcpp(
                mandatorId, oib, formDateStr, formTypeCode,
                seqNum, versionNum, dateFromStr, dateToStr, companyId);

        // --- 3. Execute INSERT INTO KMAQCPP (MI-MV Detalj) ---
        as400Repository.executeInsertKmaqcpp(
                mandatorId, oib, formDateStr, formTypeCode,
                seqNum, versionNum, dateFromStr, dateToStr, companyId);

        // --- 4. SELECT from KLCFCPP (zaglavlje) to fill response header ---
        List<KlcfcppRecord> headers = as400Repository.fetchKlcfcpp(companyCode, request.getDateFrom(), request.getDateTo());
        log.info("Fetched {} KLCFCPP header records for companyCode={}", headers.size(), companyCode);

        String companyDesc   = headers.isEmpty() ? ("Company " + companyId)  : headers.get(0).getNazivObveznika();
        String companySeat   = headers.isEmpty() ? null                      : headers.get(0).getSjedisteObveznika();
        String taxOfficeCode = request.getTaxOfficeCode() != null ? request.getTaxOfficeCode()
                : (headers.isEmpty() ? null : headers.get(0).getCarinskiUred());
        String taxOfficeDesc = request.getTaxOfficeDescription() != null ? request.getTaxOfficeDescription()
                : (headers.isEmpty() ? null : headers.get(0).getCarinskiUredOpis());
        String email = request.getDestinationEmail() != null ? request.getDestinationEmail()
                : (headers.isEmpty() ? null : headers.get(0).getEmailAdresa());

        BigDecimal totalPP = headers.stream()
                .map(f -> f.getUkIznosPP() != null ? f.getUkIznosPP() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = headers.stream()
                .map(f -> f.getUkIznosUplacenogPP() != null ? f.getUkIznosUplacenogPP() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- 5. SELECT from KLCGCPP + KMAQCPP to fill vehiclesToTax ---
        List<VehicleTaxItem> vehicles = as400Repository.fetchVehicleTaxItems(
                mandatorId, oib, formDateStr, formTypeCode, seqNum, versionNum);
        log.info("Fetched {} vehicle tax items", vehicles.size());

        // Build composite identifier: oib-formDate-formTypeCode-seqNum-versionNum
        // Example: 30985203273-01082014-405-01-001
        String compositeId = String.format("%s-%s-%s-%02d-%03d",
                oib != null ? oib : "", formDateStr, formTypeCode != null ? formTypeCode : "",
                seqNum, versionNum);

        return FormBuildResponse.builder()
                .id(compositeId)
                .dateFrom(request.getDateFrom())
                .dateTo(request.getDateTo())
                .taxNewVehiclesSum(totalPP)
                .taxUsedVehiclesSum(totalPaid)
                .taxPayersTypeSelected(request.getTaxPayerCode())
                .mandatorDescription(mandatorId)
                .companyDescription(companyDesc)
                .companySeat(companySeat)
                .taxOfficeCode(taxOfficeCode)
                .taxOfficeDescription(taxOfficeDesc)
                .destinationEmail(email)
                .isUsed(false)
                .vehiclesToTax(vehicles)
                .build();
    }

    /**
     * Returns a preview of existing MI-MV processed entries along with the selected taxpayer type (dummy data).
     * <p>
     * TODO: Replace with AS400 DB2 queries to fetch actual processed form history.
     */
    public PreviewExistingResponse getPreviewExisting(String mandatorId, String companyId) {
        log.info("Fetching existing preview for mandatorId={}, companyId={}", mandatorId, companyId);

        // TODO: Replace with AS400 DB2 call
        TaxPayerType selectedType = TaxPayerType.builder()
                .taxPayerCode("02")
                .taxPayerDescription("Trader (Trgovac)")
                .selected(true)
                .build();

        List<MimvProcessedItem> processed = List.of(
                MimvProcessedItem.builder()
                        .identificator("30985203273-01082014-405-01-001")
                        .action("N")
                        .dateFrom(LocalDate.of(2014, 8, 1))
                        .dateTo(LocalDate.of(2014, 8, 31))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01042017-405-01-001")
                        .action("N")
                        .dateFrom(LocalDate.of(2017, 4, 1))
                        .dateTo(LocalDate.of(2017, 4, 30))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01102017-405-01-001")
                        .action("N")
                        .dateFrom(LocalDate.of(2017, 10, 1))
                        .dateTo(LocalDate.of(2017, 10, 31))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01012018-405-01-001")
                        .action("N")
                        .dateFrom(LocalDate.of(2018, 1, 1))
                        .dateTo(LocalDate.of(2018, 1, 31))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01052018-405-01-001")
                        .action("N")
                        .dateFrom(LocalDate.of(2018, 5, 1))
                        .dateTo(LocalDate.of(2018, 5, 31))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01062018-405-01-001")
                        .action("N")
                        .dateFrom(LocalDate.of(2018, 6, 1))
                        .dateTo(LocalDate.of(2018, 6, 30))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01062018-405-01-002")
                        .action("A")
                        .dateFrom(LocalDate.of(2018, 6, 1))
                        .dateTo(LocalDate.of(2018, 6, 30))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01072018-405-01-001")
                        .action("N")
                        .dateFrom(LocalDate.of(2018, 7, 1))
                        .dateTo(LocalDate.of(2018, 7, 31))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01072018-405-01-002")
                        .action("A")
                        .dateFrom(LocalDate.of(2018, 7, 1))
                        .dateTo(LocalDate.of(2018, 7, 31))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01082018-405-01-001")
                        .action("N")
                        .dateFrom(LocalDate.of(2018, 8, 1))
                        .dateTo(LocalDate.of(2018, 8, 31))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01082018-405-01-002")
                        .action("A")
                        .dateFrom(LocalDate.of(2018, 8, 1))
                        .dateTo(LocalDate.of(2018, 8, 31))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("30985203273-01082018-405-01-003")
                        .action("A")
                        .dateFrom(LocalDate.of(2018, 8, 1))
                        .dateTo(LocalDate.of(2018, 8, 31))
                        .build()
        );

        return PreviewExistingResponse.builder()
                .taxPayerType(selectedType)
                .mimvProcessed(processed)
                .build();
    }

    /**
     * Returns a list of vehicle brands from AS400 DB2.
     * <p>
     * Query: SELECT CHYSAA, CHL6AQ FROM IVASXT.KLCHCPP
     */
    public BrandsResponse getBrands(String mandatorId, String companyId) {
        log.info("Fetching vehicle brands from AS400 for mandatorId={}, companyId={}", mandatorId, companyId);
        List<Brand> brands = as400Repository.fetchBrands();
        return BrandsResponse.builder()
                .brands(brands)
                .build();
    }

    /**
     * Returns a list of tax offices from AS400 DB2.
     * <p>
     * Query: SELECT CIYTAA, CIL7AQ FROM IVASXT.KLCICPP
     */
    public TaxOfficesResponse getTaxOffices(String mandatorId, String companyId) {
        log.info("Fetching tax offices from AS400 for mandatorId={}, companyId={}", mandatorId, companyId);
        List<TaxOffice> offices = as400Repository.fetchTaxOffices();
        return TaxOfficesResponse.builder()
                .taxOffices(offices)
                .build();
    }
}



