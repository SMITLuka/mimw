package com.smit.mimw.controller;

import com.smit.mimw.dto.*;
import com.smit.mimw.service.TaxPayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mimv")
public class TaxPayerController {

    private final TaxPayerService taxPayerService;

    public TaxPayerController(TaxPayerService taxPayerService) {
        this.taxPayerService = taxPayerService;
    }

    /**
     * GET /mimv/taxpayer/types
     *
     * Returns a list of all taxpayer types.
     *
     * Required headers: mandatorId, companyId
     */
    @GetMapping("/taxpayer/types")
    public ResponseEntity<ApiResponse<TaxPayerTypesResponse>> getTaxPayerTypes(
            @RequestHeader("mandatorId") String mandatorId,
            @RequestHeader("companyId") String companyId) {

        TaxPayerTypesResponse response = taxPayerService.getTaxPayerTypes(mandatorId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Taxpayer types fetched successfully."));
    }

    /**
     * POST /mimv/taxpayer/type
     *
     * Saves a new taxpayer type.
     *
     * Required headers: mandatorId, companyId
     */
    @PostMapping("/taxpayer/type")
    public ResponseEntity<ApiResponse<IsSuccessResponse>> saveTaxPayerType(
            @RequestHeader("mandatorId") String mandatorId,
            @RequestHeader("companyId") String companyId,
            @RequestBody List<TaxPayerTypeRequest> requests) {

        IsSuccessResponse response = taxPayerService.saveTaxPayerType(mandatorId, companyId, requests);
        return ResponseEntity.ok(ApiResponse.ok(response, "Taxpayer type saved successfully."));
    }

    /**
     * POST /mimv/form/build
     *
     * Builds a MI-MV form based on the provided parameters.
     * Returns form header data along with a list of vehicles to tax.
     *
     * Required headers: mandatorId, companyId
     */
    @PostMapping("/form/build")
    public ResponseEntity<ApiResponse<FormBuildResponse>> buildForm(
            @RequestHeader("mandatorId") String mandatorId,
            @RequestHeader("companyId") String companyId,
            @RequestBody FormBuildRequest request) {

        FormBuildResponse response = taxPayerService.buildForm(mandatorId, companyId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Form built successfully."));
    }

    /**
     * GET /mimv/preview/existing
     *
     * Returns the selected taxpayer type and a list of already processed MI-MV forms.
     *
     * Required headers: mandatorId, companyId
     */
    @GetMapping("/preview/existing")
    public ResponseEntity<ApiResponse<PreviewExistingResponse>> getPreviewExisting(
            @RequestHeader("mandatorId") String mandatorId,
            @RequestHeader("companyId") String companyId) {

        PreviewExistingResponse response = taxPayerService.getPreviewExisting(mandatorId, companyId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Existing preview fetched successfully."));
    }
}

