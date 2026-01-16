package com.example.failuresim.failures.json;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates a backward-incompatible JSON change by renaming a field.
 *
 * <p><strong>What fails:</strong> old clients deserializing \"name\" break when field becomes
 * \"fullName\".</p>
 * <p><strong>Why in production:</strong> API contracts evolve without versioning or compatibility.</p>
 * <p><strong>Symptoms:</strong> client errors, empty fields, or runtime deserialization failures.</p>
 * <p><strong>Metrics/logs:</strong> client error rates, schema validation errors.</p>
 * <p><strong>Fix:</strong> version APIs, add new fields without removing old ones, or use
 * tolerant readers.</p>
 */
@RestController
@RequestMapping("/fail/json")
public class JsonContractController {

    @GetMapping("/break")
    public ResponseEntity<Object> contractBreak(
            @RequestParam(name = "version", defaultValue = "new") String version) {
        if ("old".equalsIgnoreCase(version)) {
            return ResponseEntity.ok(new OldUserResponse("Ada Lovelace"));
        }

        // Breaking change: field renamed from "name" to "fullName" with no versioning.
        return ResponseEntity.ok(new NewUserResponse("Ada Lovelace"));
    }

    public record OldUserResponse(String name) {
    }

    public record NewUserResponse(String fullName) {
    }
}
