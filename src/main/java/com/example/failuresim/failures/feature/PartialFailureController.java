package com.example.failuresim.failures.feature;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PartialFailureController {

    /**
     * Failure: Partial dependency failure with graceful degradation.
     * Why: Optional downstream fails; without degradation, the entire request fails.
     * Symptom: 500s for optional data, poor UX.
     * Observability: Dependency error rate, degraded response counters.
     * Fix: Return partial results, feature flags, fallbacks.
     */
    @GetMapping("/fail/feature/optional")
    public ResponseEntity<Map<String, Object>> optionalDependency(
            @RequestParam(defaultValue = "false") boolean failOptional) {

        Map<String, Object> response = new HashMap<>();
        response.put("coreData", "always-available-core-result");

        try {
            if (failOptional) {
                throw new IllegalStateException("Optional dependency is down.");
            }
            response.put("optionalData", "optional-enrichment-result");
            response.put("degraded", false);
        } catch (Exception ex) {
            // Degrade gracefully: deliver core response with an explicit flag.
            response.put("optionalData", null);
            response.put("degraded", true);
            response.put("warning", ex.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}
