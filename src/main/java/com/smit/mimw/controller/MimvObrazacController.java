package com.smit.mimw.controller;

import com.smit.mimw.dto.ApiResponse;
import com.smit.mimw.dto.MimvObrazac;
import com.smit.mimw.service.MimvObrazacService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mimv")
public class MimvObrazacController {

    private final MimvObrazacService mimvObrazacService;

    public MimvObrazacController(MimvObrazacService mimvObrazacService) {
        this.mimvObrazacService = mimvObrazacService;
    }

    /**
     * GET /api/mimv/obrazac?godina=2026&mjesec=3
     * <p>
     * Vraća MI-MV obrazac (zasad dummy podaci).
     */
    @GetMapping("/obrazac")
    public ResponseEntity<ApiResponse<MimvObrazac>> dohvatiObrazac(
            @RequestParam(required = false, defaultValue = "2026") Integer godina,
            @RequestParam(required = false, defaultValue = "1") Integer mjesec) {

        MimvObrazac obrazac = mimvObrazacService.dohvatiObrazac(godina, mjesec);
        return ResponseEntity.ok(ApiResponse.ok(obrazac, "Obrazac uspješno dohvaćen."));
    }

    /**
     * POST /api/mimv/obrazac
     * <p>
     * Zaprima MI-MV obrazac podatke za spremanje.
     */
    @PostMapping("/obrazac")
    public ResponseEntity<ApiResponse<MimvObrazac>> spremiObrazac(@RequestBody MimvObrazac obrazac) {

        MimvObrazac spremljeni = mimvObrazacService.spremiObrazac(obrazac);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(spremljeni, "Obrazac uspješno zaprimljen."));
    }
}

