package com.example.failuresim.failures.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigDriftController {

    /**
     * Failure: Configuration drift / missing env vars.
     * Why: Production environment lacks a required variable configured in lower envs.
     * Symptom: Feature disabled or runtime errors only in prod.
     * Observability: Startup config audits, config validation logs.
     * Fix: Validate config on startup, enforce config checks in CI/CD.
     */
    @GetMapping("/fail/config/missing")
    public ResponseEntity<Map<String, Object>> missingEnvVar() {
        String value = System.getenv("CRITICAL_FEATURE_FLAG");
        if (value == null || value.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Required env var CRITICAL_FEATURE_FLAG is missing.");
            response.put("impact", "Feature disabled in production even though it worked in staging.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Feature flag loaded successfully.");
        response.put("value", value);
        return ResponseEntity.ok(response);
    }
}
