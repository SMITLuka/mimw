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
        // Fetch form header from local Pantheon MSSQL table mimv_zaglavlje
        // ---------------------------------------------------------------
        FormBuildResponse zaglavlje = as400Repository.fetchMimvZaglavlje();
        if (zaglavlje != null) {
            log.info("Fetched mimv_zaglavlje row: id={}", zaglavlje.getId());
            return zaglavlje;
        }
        log.warn("mimv_zaglavlje returned no data — falling through to AS400 flow");
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



