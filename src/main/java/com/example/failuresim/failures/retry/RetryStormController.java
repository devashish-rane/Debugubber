package com.example.failuresim.failures.retry;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetryStormController {

    /**
     * Failure: Retry storm amplification.
     * Why: Each request multiplies into retries, compounding load on an already failing service.
     * Symptom: Sudden traffic spikes, self-inflicted overload, noisy logs.
     * Observability: Retry counters, error rates, downstream RPS.
     * Fix: Cap retries, add jitter/backoff, use circuit breakers.
     * Safety: Max retries and requests are capped.
     */
    private static final Logger logger = LoggerFactory.getLogger(RetryStormController.class);

    private static final int MAX_REQUESTS = 5;
    private static final int MAX_RETRIES = 3;

    @PostMapping("/fail/retry/storm")
    public ResponseEntity<Map<String, Object>> retryStorm(
            @RequestParam(defaultValue = "3") int requests,
            @RequestParam(defaultValue = "2") int maxRetries,
            @RequestParam(defaultValue = "1") int failFirstAttempts) {

        int safeRequests = Math.min(requests, MAX_REQUESTS);
        int safeRetries = Math.min(maxRetries, MAX_RETRIES);
        int safeFailFirst = Math.max(0, Math.min(failFirstAttempts, safeRetries));

        int totalAttempts = 0;
        int failures = 0;
        for (int i = 1; i <= safeRequests; i++) {
            int attempt = 0;
            boolean success = false;
            while (attempt <= safeRetries && !success) {
                attempt++;
                totalAttempts++;
                if (attempt <= safeFailFirst) {
                    failures++;
                    logger.warn("Request {} failed on attempt {}", i, attempt);
                } else {
                    success = true;
                    logger.info("Request {} succeeded on attempt {}", i, attempt);
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Simulated retry amplification (safe bounded).");
        response.put("requests", safeRequests);
        response.put("maxRetries", safeRetries);
        response.put("failFirstAttempts", safeFailFirst);
        response.put("totalAttempts", totalAttempts);
        response.put("failures", failures);
        return ResponseEntity.ok(response);
    }
}
