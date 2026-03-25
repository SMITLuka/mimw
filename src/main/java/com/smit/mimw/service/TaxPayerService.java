package com.smit.mimw.service;

import com.smit.mimw.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for taxpayer type and form-build operations.
 * <p>
 * TODO: Replace all dummy implementations with AS400 DB2 calls.
 */
@Service
public class TaxPayerService {

    private static final Logger log = LoggerFactory.getLogger(TaxPayerService.class);

    /**
     * Returns a list of taxpayer types (dummy data).
     * <p>
     * TODO: Replace with AS400 DB2 query, e.g.:
     *   jdbcTemplate.query("SELECT TAX_CODE, TAX_DESC FROM TAX_PAYER_TYPES WHERE MANDATOR_ID=? AND COMPANY_ID=?", ...)
     */
    public TaxPayerTypesResponse getTaxPayerTypes(String mandatorId, String companyId) {
        log.info("Fetching taxpayer types for mandatorId={}, companyId={}", mandatorId, companyId);

        // TODO: Replace with AS400 DB2 call
        List<TaxPayerType> dummyTypes = List.of(
                TaxPayerType.builder()
                        .taxPayerCode("01")
                        .taxPayerDescription("Manufacturer (Proizvođač)")
                        .selected(false)
                        .build(),
                TaxPayerType.builder()
                        .taxPayerCode("02")
                        .taxPayerDescription("Trader (Trgovac)")
                        .selected(true)
                        .build(),
                TaxPayerType.builder()
                        .taxPayerCode("03")
                        .taxPayerDescription("Registered Trader (Registrirani trgovac)")
                        .selected(false)
                        .build()
        );

        return TaxPayerTypesResponse.builder()
                .taxPayerTypes(dummyTypes)
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
     * Builds a MI-MV form with dummy data based on the request.
     * <p>
     * TODO: Replace with AS400 DB2 queries to fetch actual form and vehicle data.
     */
    public FormBuildResponse buildForm(String mandatorId, String companyId, FormBuildRequest request) {
        log.info("Building form for mandatorId={}, companyId={}, taxPayerCode={}, dateFrom={}, dateTo={}",
                mandatorId, companyId, request.getTaxPayerCode(), request.getDateFrom(), request.getDateTo());

        // TODO: Replace with AS400 DB2 calls to fetch real form header and vehicle rows

        List<VehicleTaxItem> dummyVehicles = List.of(
                VehicleTaxItem.builder()
                        .id(1L)
                        .status("NEW")
                        .type("M1")
                        .make("Volkswagen")
                        .description("Golf 8 Style 1.5 TSI, automatic, silver metallic")
                        .vin("WVWZZZ1KZMP012345")
                        .fuelType("Petrol")
                        .dateFirstRegistration(LocalDate.of(2026, 1, 15))
                        .co2(126.0)
                        .build(),
                VehicleTaxItem.builder()
                        .id(2L)
                        .status("USED")
                        .type("M1")
                        .make("BMW")
                        .description("320d xDrive, automatic, black")
                        .vin("WBA8E1C05JA987654")
                        .fuelType("Diesel")
                        .dateFirstRegistration(LocalDate.of(2022, 6, 10))
                        .co2(134.0)
                        .build()
        );

        return FormBuildResponse.builder()
                .id(1001L)
                .dateFrom(request.getDateFrom() != null ? request.getDateFrom() : LocalDate.now().withDayOfMonth(1))
                .dateTo(request.getDateTo() != null ? request.getDateTo() : LocalDate.now())
                .taxNewVehiclesSum(new BigDecimal("4500.00"))
                .taxUsedVehiclesSum(new BigDecimal("2100.00"))
                .taxPayersTypeSelected(request.getTaxPayerCode())
                .mandatorDescription("Mandator " + mandatorId)
                .companyDescription("Company " + companyId)
                .taxOfficeCode(request.getTaxOfficeCode())
                .taxOfficeDescription(request.getTaxOfficeDescription())
                .destinationEmail(request.getDestinationEmail())
                .isUsed(false)
                .vehiclesToTax(dummyVehicles)
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
                        .identificator("MIMV-2026-001")
                        .action("SUBMITTED")
                        .dateFrom(LocalDate.of(2026, 1, 1))
                        .dateTo(LocalDate.of(2026, 1, 31))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("MIMV-2026-002")
                        .action("APPROVED")
                        .dateFrom(LocalDate.of(2026, 2, 1))
                        .dateTo(LocalDate.of(2026, 2, 28))
                        .build(),
                MimvProcessedItem.builder()
                        .identificator("MIMV-2026-003")
                        .action("DRAFT")
                        .dateFrom(LocalDate.of(2026, 3, 1))
                        .dateTo(LocalDate.of(2026, 3, 31))
                        .build()
        );

        return PreviewExistingResponse.builder()
                .taxPayerType(selectedType)
                .mimvProcessed(processed)
                .build();
    }
}



