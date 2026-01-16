package com.example.failuresim.failures.downstream;

import java.time.Duration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock downstream dependency hosted in the same application for safe testing.
 *
 * <p>This endpoint is intentionally slow or failing to support upstream scenarios.</p>
 */
@RestController
@RequestMapping("/internal/downstream")
public class InternalDownstreamController {
    private static final Duration MAX_DELAY = Duration.ofSeconds(10);

    @GetMapping("/slow")
    public ResponseEntity<String> slowEndpoint(
            @RequestParam(name = "delayMs", defaultValue = "1000") long delayMs)
            throws InterruptedException {
        long safeDelayMs = Math.min(delayMs, MAX_DELAY.toMillis());
        Thread.sleep(safeDelayMs);
        return ResponseEntity.ok("Slept " + safeDelayMs + "ms");
    }

    @GetMapping("/fail")
    public ResponseEntity<String> failingEndpoint() {
        return ResponseEntity.status(500).body("Simulated downstream failure");
    }
}
