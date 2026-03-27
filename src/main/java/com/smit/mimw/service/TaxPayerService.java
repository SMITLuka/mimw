package com.smit.mimw.service;

import com.smit.mimw.dto.*;
import com.smit.mimw.repository.As400Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for taxpayer type and form-build operations.
 * <p>
 * Three methods are backed by live AS400 DB2 queries (via As400Repository).
 * The remaining methods still return dummy data — replace with AS400 calls when ready.
 */
@Service
public class TaxPayerService {

    private static final Logger log = LoggerFactory.getLogger(TaxPayerService.class);

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
     * Builds a MI-MV form by fetching real data from IVAS0000B0.KLCFCPP.
     *
     * Filters applied:
     *   - CFNSRO = companyId (6-char padded, e.g. "000080" for companyId "80")
     *   - CFI3AG >= dateFrom  (period start, YYYYMMDD)
     *   - CFI4AG <= dateTo    (period end,   YYYYMMDD)
     */
    public FormBuildResponse buildForm(String mandatorId, String companyId, FormBuildRequest request) {
        log.info("Building form for mandatorId={}, companyId={}, taxPayerCode={}, dateFrom={}, dateTo={}",
                mandatorId, companyId, request.getTaxPayerCode(), request.getDateFrom(), request.getDateTo());

        // Pad companyId to 6 chars if it's numeric (DB stores "000080" for company 80)
        String companyCode = companyId;
        if (companyId != null && companyId.matches("\\d+")) {
            companyCode = String.format("%06d", Long.parseLong(companyId));
        }

        List<KlcfcppRecord> forms = as400Repository.fetchKlcfcpp(
                companyCode, request.getDateFrom(), request.getDateTo());

        log.info("Fetched {} KLCFCPP records for companyCode={}", forms.size(), companyCode);

        // Derive header fields from first record (when available)
        String companyDesc  = forms.isEmpty() ? ("Company " + companyId) : forms.get(0).getNazivObveznika();
        String taxOfficeCode = request.getTaxOfficeCode() != null ? request.getTaxOfficeCode()
                : (forms.isEmpty() ? null : forms.get(0).getCarinskiUred());
        String taxOfficeDesc = request.getTaxOfficeDescription() != null ? request.getTaxOfficeDescription()
                : (forms.isEmpty() ? null : forms.get(0).getCarinskiUredOpis());
        String email = request.getDestinationEmail() != null ? request.getDestinationEmail()
                : (forms.isEmpty() ? null : forms.get(0).getEmailAdresa());

        // Sum totals across all fetched forms
        BigDecimal totalPP = forms.stream()
                .map(f -> f.getUkIznosPP() != null ? f.getUkIznosPP() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = forms.stream()
                .map(f -> f.getUkIznosUplacenogPP() != null ? f.getUkIznosUplacenogPP() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return FormBuildResponse.builder()
                .dateFrom(request.getDateFrom())
                .dateTo(request.getDateTo())
                .taxNewVehiclesSum(totalPP)
                .taxUsedVehiclesSum(totalPaid)
                .taxPayersTypeSelected(request.getTaxPayerCode())
                .mandatorDescription("Mandator " + mandatorId)
                .companyDescription(companyDesc)
                .taxOfficeCode(taxOfficeCode)
                .taxOfficeDescription(taxOfficeDesc)
                .destinationEmail(email)
                .isUsed(false)
                .forms(forms)
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



