package com.smit.mimw.controller;

import com.smit.mimw.dto.ApiResponse;
import com.smit.mimw.dto.BrandsResponse;
import com.smit.mimw.dto.FormBuildRequest;
import com.smit.mimw.dto.FormBuildResponse;
import com.smit.mimw.dto.FormUpdateRequest;
import com.smit.mimw.dto.IsSuccessResponse;
import com.smit.mimw.dto.PreviewExistingResponse;
import com.smit.mimw.dto.TaxOfficesResponse;
import com.smit.mimw.dto.TaxPayerTypeRequest;
import com.smit.mimw.dto.TaxPayerTypesResponse;
import com.smit.mimw.service.MimvFormService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for all MIMV form endpoints.
 * Required headers on every request: mandatorId, companyId.
 */
@RestController
@RequestMapping("/mimv") //$NON-NLS-1$
public class MimvFormController {

    private final MimvFormService mimvFormService;

    public MimvFormController(MimvFormService mimvFormService) {
        this.mimvFormService = mimvFormService;
    }

    /**
     * GET /mimv/taxpayer/types
     * Returns all MIMV taxpayer type codes and descriptions from AS400.
     */
    @GetMapping("/taxpayer/types") //$NON-NLS-1$
    public ResponseEntity<ApiResponse<TaxPayerTypesResponse>> getTaxPayerTypes(
            @RequestHeader("mandatorId") String mandatorId, //$NON-NLS-1$
            @RequestHeader("companyId") String companyId) { //$NON-NLS-1$

        TaxPayerTypesResponse response = mimvFormService.getTaxPayerTypes(mandatorId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Taxpayer types fetched successfully.")); //$NON-NLS-1$
    }

    /**
     * POST /mimv/taxpayer/type
     * Saves taxpayer type selection.
     */
    @PostMapping("/taxpayer/type") //$NON-NLS-1$
    public ResponseEntity<ApiResponse<IsSuccessResponse>> saveTaxPayerType(
            @RequestHeader("mandatorId") String mandatorId, //$NON-NLS-1$
            @RequestHeader("companyId") String companyId, //$NON-NLS-1$
            @RequestBody List<TaxPayerTypeRequest> requests) {

        IsSuccessResponse response = mimvFormService.saveTaxPayerType(mandatorId, companyId, requests);
        return ResponseEntity.ok(ApiResponse.ok(response, "Taxpayer type saved successfully.")); //$NON-NLS-1$
    }

    /**
     * POST /mimv/form/build
     * Builds a MIMV form: inserts MIMV_DETALJ from AS400 HF tables,
     * computes totals, then inserts MIMV_ZAGLAVLJE. Returns the saved header.
     */
    @PostMapping("/form/build") //$NON-NLS-1$
    public ResponseEntity<ApiResponse<FormBuildResponse>> buildForm(
            @RequestHeader("mandatorId") String mandatorId, //$NON-NLS-1$
            @RequestHeader("companyId") String companyId, //$NON-NLS-1$
            @RequestBody FormBuildRequest request) {

        FormBuildResponse response = mimvFormService.buildForm(mandatorId, companyId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Form built successfully.")); //$NON-NLS-1$
    }

    /**
     * POST /mimv/form/build/update
     * Replaces an existing MIMV form in Pantheon MSSQL using caller-supplied data.
     * No AS400 reads are performed — all zaglavlje and detalji data comes from the request body.
     * The form is identified by the key fields inside request.data.zaglavlje
     * (oibObveznika, datumPP, sifraObrascaPP, redniBrojPP, redniBrojPPProm).
     */
    @PostMapping("/form/build/update") //$NON-NLS-1$
    public ResponseEntity<ApiResponse<FormBuildResponse>> updateForm(
            @RequestHeader("mandatorId") String mandatorId, //$NON-NLS-1$
            @RequestHeader("companyId") String companyId, //$NON-NLS-1$
            @RequestBody FormUpdateRequest request)
    {
        FormBuildResponse response = mimvFormService.updateForm(mandatorId, companyId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Form updated successfully.")); //$NON-NLS-1$
    }

    /**
     * GET /mimv/preview/existing
     * Returns all previously submitted MIMV forms from MIMV_ZAGLAVLJE.
     */
    @GetMapping("/preview/existing") //$NON-NLS-1$
    public ResponseEntity<ApiResponse<PreviewExistingResponse>> getPreviewExisting(
            @RequestHeader("mandatorId") String mandatorId, //$NON-NLS-1$
            @RequestHeader("companyId") String companyId) { //$NON-NLS-1$

        PreviewExistingResponse response = mimvFormService.getPreviewExisting(mandatorId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Existing forms fetched successfully.")); //$NON-NLS-1$
    }

    /**
     * GET /mimv/brands
     * Returns all vehicle brands from AS400.
     */
    @GetMapping("/brands") //$NON-NLS-1$
    public ResponseEntity<ApiResponse<BrandsResponse>> getBrands(
            @RequestHeader("mandatorId") String mandatorId, //$NON-NLS-1$
            @RequestHeader("companyId") String companyId) { //$NON-NLS-1$

        BrandsResponse response = mimvFormService.getBrands(mandatorId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Brands fetched successfully.")); //$NON-NLS-1$
    }

    /**
     * GET /mimv/tax/offices
     * Returns all customs/tax offices from AS400.
     */
    @GetMapping("/tax/offices") //$NON-NLS-1$
    public ResponseEntity<ApiResponse<TaxOfficesResponse>> getTaxOffices(
            @RequestHeader("mandatorId") String mandatorId, //$NON-NLS-1$
            @RequestHeader("companyId") String companyId) { //$NON-NLS-1$

        TaxOfficesResponse response = mimvFormService.getTaxOffices(mandatorId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Tax offices fetched successfully.")); //$NON-NLS-1$
    }
}
