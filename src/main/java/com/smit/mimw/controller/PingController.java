package com.smit.mimw.controller;

import com.smit.mimw.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PingController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Boolean>> ping() {
        return ApiResponse.ok(Map.of("success", true), "pong");
    }
}

