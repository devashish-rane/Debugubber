package com.example.failuresim.failures.startup;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ColdStartController {

    /**
     * Failure: Cold start / warmup latency spikes.
     * Why: JIT compilation, cache warming, lazy init, class loading.
     * Symptom: First request slow, later requests fast.
     * Observability: Cold-start latency, warmup traces, cache miss metrics.
     * Fix: Warmup endpoints, precompute caches, lazy-init review.
     * Safety: Warmup delay capped.
     */
    private static final AtomicBoolean WARMED = new AtomicBoolean(false);
    private static final long MAX_WARMUP_MS = 3_000L;

    @GetMapping("/fail/startup/cold")
    public ResponseEntity<Map<String, Object>> coldStart(
            @RequestParam(defaultValue = "1500") long warmupMs) throws InterruptedException {

        long safeWarmup = Math.min(warmupMs, MAX_WARMUP_MS);

        Map<String, Object> response = new HashMap<>();
        if (WARMED.compareAndSet(false, true)) {
            Thread.sleep(safeWarmup);
            response.put("message", "Cold start: first request paid warmup cost.");
            response.put("warmupDelay", Duration.ofMillis(safeWarmup).toString());
        } else {
            response.put("message", "Warm request: cached/JIT warmed path.");
            response.put("warmupDelay", Duration.ZERO.toString());
        }

        return ResponseEntity.ok(response);
    }
}
