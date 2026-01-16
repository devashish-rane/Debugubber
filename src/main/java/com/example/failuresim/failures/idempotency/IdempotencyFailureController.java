package com.example.failuresim.failures.idempotency;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdempotencyFailureController {

    /**
     * Failure: Idempotency failure leading to duplicate processing.
     * Why: Retries replay non-idempotent operations without dedupe keys.
     * Symptom: Duplicate orders/charges, inconsistent state.
     * Observability: Duplicate identifiers, replay logs, idempotency key stats.
     * Fix: Require idempotency keys, store results, deduplicate on retries.
     */
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger(1000);
    private static final Set<String> IDEMPOTENCY_KEYS = ConcurrentHashMap.newKeySet();

    @PostMapping("/fail/idempotency/duplicate")
    public ResponseEntity<Map<String, Object>> duplicateOrder(
            @RequestParam(defaultValue = "order-123") String requestId,
            @RequestParam(defaultValue = "false") boolean useIdempotencyKey) {

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);

        if (useIdempotencyKey && IDEMPOTENCY_KEYS.contains(requestId)) {
            response.put("message", "Duplicate request detected, returning previous result.");
            response.put("status", "duplicate");
            return ResponseEntity.ok(response);
        }

        IDEMPOTENCY_KEYS.add(requestId);
        int orderId = ORDER_COUNTER.incrementAndGet();
        response.put("message", "Processed request and created an order.");
        response.put("status", "created");
        response.put("orderId", orderId);
        response.put("warning", useIdempotencyKey
                ? "Idempotency key missing in earlier request created duplicates."
                : "No idempotency protection; retries create duplicates.");

        return ResponseEntity.ok(response);
    }
}
