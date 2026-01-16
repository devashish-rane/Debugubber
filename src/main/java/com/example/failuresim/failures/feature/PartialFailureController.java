package com.example.failuresim.failures.feature;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates graceful degradation when an optional dependency fails.
 *
 * <p><strong>What fails:</strong> optional dependency fails, but core response succeeds.</p>
 * <p><strong>Why in production:</strong> not all dependencies are equally critical.</p>
 * <p><strong>Symptoms:</strong> partial data, warning logs, but no full outage.</p>
 * <p><strong>Metrics/logs:</strong> dependency error rate, fallback usage, partial responses.</p>
 * <p><strong>Fix:</strong> isolate optional features and provide clear fallback behavior.</p>
 */
@RestController
@RequestMapping("/fail/feature")
public class PartialFailureController {

    @GetMapping("/optional")
    public ResponseEntity<Map<String, Object>> optionalFeature() {
        Map<String, Object> response = new HashMap<>();
        response.put("requiredData", "always available");

        try {
            throw new IllegalStateException("Optional dependency timeout");
        } catch (Exception ex) {
            response.put("optionalData", null);
            response.put("warning", "Optional dependency failed; returning partial response.");
        }

        return ResponseEntity.ok(response);
    }
}
