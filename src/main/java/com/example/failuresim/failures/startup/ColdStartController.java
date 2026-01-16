package com.example.failuresim.failures.startup;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates cold start behavior: first request is slow, subsequent ones are fast.
 *
 * <p><strong>What fails:</strong> first user experiences high latency.</p>
 * <p><strong>Why in production:</strong> JIT warmup, cache misses, or lazy initialization.</p>
 * <p><strong>Symptoms:</strong> slow first requests after deploy/scale-up.</p>
 * <p><strong>Metrics/logs:</strong> cold-start latency spikes, cache hit rates.</p>
 * <p><strong>Fix:</strong> warm up caches, pre-load data, or run synthetic traffic.</p>
 */
@RestController
@RequestMapping("/fail/startup")
public class ColdStartController {
    private static final Duration MAX_DELAY = Duration.ofSeconds(5);
    private final AtomicBoolean warmed = new AtomicBoolean(false);

    @GetMapping("/cold")
    public ResponseEntity<String> coldStart(
            @RequestParam(name = "delayMs", defaultValue = "2000") long delayMs)
            throws InterruptedException {
        if (warmed.compareAndSet(false, true)) {
            long safeDelayMs = Math.min(delayMs, MAX_DELAY.toMillis());
            Thread.sleep(safeDelayMs);
            return ResponseEntity.ok("Cold start path executed with " + safeDelayMs + "ms delay.");
        }

        return ResponseEntity.ok("Warm path: fast response.");
    }
}
