package com.example.failuresim.failures.human;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates a human error where a global toggle disables a critical feature.
 *
 * <p><strong>What fails:</strong> critical feature is disabled by a config toggle.</p>
 * <p><strong>Why in production:</strong> manual config changes bypass validation/guardrails.</p>
 * <p><strong>Symptoms:</strong> sudden feature outage with no code deploy.</p>
 * <p><strong>Metrics/logs:</strong> config change audit logs, feature usage drop.</p>
 * <p><strong>Fix:</strong> add change review, validation, and safe defaults.</p>
 */
@RestController
@RequestMapping("/fail/human")
public class HumanErrorController {

    @GetMapping("/misconfig")
    public ResponseEntity<String> misconfig() {
        String disabled = System.getenv().getOrDefault("GLOBAL_DISABLE_FEATURE", "false");
        if ("true".equalsIgnoreCase(disabled)) {
            return ResponseEntity.status(503)
                    .body("Feature disabled globally due to misconfiguration. Guardrails should prevent this.");
        }
        return ResponseEntity.ok("Feature enabled. Toggle GLOBAL_DISABLE_FEATURE to simulate human error.");
    }
}
