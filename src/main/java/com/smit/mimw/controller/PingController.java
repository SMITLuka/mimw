package com.smit.mimw.controller;

import com.smit.mimw.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PingController {

    private static final Logger log = LoggerFactory.getLogger(PingController.class);

    @Autowired
    private DataSource dataSource;

    @GetMapping("/ping")
    public ApiResponse<Map<String, Boolean>> ping() {
        return ApiResponse.ok(Map.of("success", true), "pong");
    }

    @GetMapping("/health/db")
    public ApiResponse<Map<String, Object>> checkDatabase() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("timestamp", System.currentTimeMillis());
        
        try (Connection conn = dataSource.getConnection()) {
            boolean isValid = conn.isValid(5); // 5 second timeout
            status.put("connected", true);
            status.put("valid", isValid);
            status.put("catalog", conn.getCatalog());
            status.put("message", "AS400 database connection successful");
            log.info("Database health check: OK");
            return ApiResponse.ok(status, "Database connection OK");
        } catch (Exception ex) {
            status.put("connected", false);
            status.put("valid", false);
            status.put("error", ex.getClass().getSimpleName());
            status.put("message", ex.getMessage());
            log.error("Database health check failed: {}", ex.getMessage());
            
            // Check if it's a timeout/connection issue
            if (ex.getMessage() != null && (ex.getMessage().contains("timed out") || 
                ex.getMessage().contains("cannot establish"))) {
                status.put("diagnosis", "Network connectivity issue - AS400 database is not reachable from this server");
                status.put("solution", "Check NETWORK_CONFIGURATION.md for solutions");
            }
            
            return ApiResponse.error(status, "Database connection failed");
        }
    }
}
