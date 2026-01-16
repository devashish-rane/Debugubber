package com.example.failuresim.failures.idempotency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shows how duplicate requests can be processed twice without idempotency keys.
 *
 * <p><strong>What fails:</strong> repeated requests cause duplicate side effects.</p>
 * <p><strong>Why in production:</strong> retries (client or gateway) re-send requests after timeouts.</p>
 * <p><strong>Symptoms:</strong> double charges, duplicated records, inconsistent state.</p>
 * <p><strong>Metrics/logs:</strong> duplicate transaction IDs, audit logs, payment reconciliation.</p>
 * <p><strong>Fix:</strong> implement idempotency keys and deduplicate operations server-side.</p>
 */
@RestController
@RequestMapping("/fail/idempotency")
public class IdempotencyFailureController {
    private final AtomicInteger totalProcessed = new AtomicInteger();
    private final Map<String, Integer> requestHistory = new ConcurrentHashMap<>();

    @PostMapping("/duplicate")
    public ResponseEntity<String> duplicateProcessing(
            @RequestParam(defaultValue = "") String requestId) {
        int current = totalProcessed.incrementAndGet();

        if (!requestId.isBlank()) {
            requestHistory.merge(requestId, 1, Integer::sum);
        }

        return ResponseEntity.ok("Processed count=" + current
                + ", requestIdOccurrences=" + requestHistory.getOrDefault(requestId, 0));
    }
}
