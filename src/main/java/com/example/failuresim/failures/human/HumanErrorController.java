package com.example.failuresim.failures.human;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HumanErrorController {

    /**
     * Failure: Human error via global kill-switch misconfiguration.
     * Why: Toggle flipped in production without guardrails/validation.
     * Symptom: Feature outage, sudden 503s.
     * Observability: Config change audit logs, feature flag metrics.
     * Fix: Require approvals, staged rollout, automated validation.
     */
    @GetMapping("/fail/human/misconfig")
    public ResponseEntity<Map<String, Object>> misconfig(
            @RequestParam(defaultValue = "false") boolean overrideDisable) {

        String env = System.getenv("GLOBAL_DISABLE_FEATURE");
        boolean disabled = Boolean.parseBoolean(env);

        if (disabled && !overrideDisable) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Feature disabled globally due to misconfigured toggle.");
            response.put("guardrail", "Add validation/approval for global kill switches.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Feature enabled.");
        response.put("disabledByEnv", disabled);
        return ResponseEntity.ok(response);
    }
}
