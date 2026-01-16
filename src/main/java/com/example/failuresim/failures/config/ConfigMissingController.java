package com.example.failuresim.failures.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates config drift by requiring an environment variable that may be missing in production.
 *
 * <p><strong>What fails:</strong> startup/runtime behavior differs between environments.</p>
 * <p><strong>Why in production:</strong> environment variables or secrets are misconfigured.</p>
 * <p><strong>Symptoms:</strong> endpoint fails only in one environment.</p>
 * <p><strong>Metrics/logs:</strong> configuration error logs, feature flag dashboards.</p>
 * <p><strong>Fix:</strong> validate config at startup and enforce deployment checks.</p>
 */
@RestController
@RequestMapping("/fail/config")
public class ConfigMissingController {

    @GetMapping("/missing")
    public ResponseEntity<String> missingEnv() {
        String featureFlag = System.getenv("REQUIRED_FEATURE_FLAG");
        if (featureFlag == null) {
            return ResponseEntity.status(500)
                    .body("Missing REQUIRED_FEATURE_FLAG env var. Works in staging, fails in prod.");
        }
        return ResponseEntity.ok("Feature flag present: " + featureFlag);
    }
}
