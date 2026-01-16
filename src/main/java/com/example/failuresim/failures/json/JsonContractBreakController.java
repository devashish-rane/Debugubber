package com.example.failuresim.failures.json;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JsonContractBreakController {

    /**
     * Failure: Backward-incompatible JSON contract change.
     * Why: Renaming/removing fields breaks older clients.
     * Symptom: Client parsing errors, failed deployments, rollback pressure.
     * Observability: Client error rates, API schema validation.
     * Fix: Version APIs, add fields without removing old ones, deprecate safely.
     */
    @GetMapping("/fail/json/break")
    public ResponseEntity<Map<String, Object>> jsonBreak(
            @RequestParam(defaultValue = "new") String version) {

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Simulated breaking API change by renaming fields.");

        if ("old".equalsIgnoreCase(version)) {
            response.put("user_id", 123);
            response.put("full_name", "Ada Lovelace");
        } else {
            // Breaking change: old clients expect user_id/full_name but now receive id/name.
            response.put("id", 123);
            response.put("name", "Ada Lovelace");
            response.put("breaking", true);
        }

        return ResponseEntity.ok(response);
    }
}
