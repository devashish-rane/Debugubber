package com.example.failuresim.failures.gc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generates high allocation churn without causing an OOM.
 *
 * <p><strong>What fails:</strong> GC pauses grow due to rapid allocations.</p>
 * <p><strong>Why in production:</strong> allocation-heavy code (JSON parsing, large buffers)
 * creates GC pressure, slowing request handling.</p>
 * <p><strong>Symptoms:</strong> latency spikes and throughput drops without CPU saturation.</p>
 * <p><strong>Metrics/logs:</strong> GC pause time, allocation rate, p99 latency.</p>
 * <p><strong>Fix:</strong> reduce allocations, reuse buffers, or tune GC/heap sizing.</p>
 */
@RestController
@RequestMapping("/fail/gc")
public class GcPressureController {
    private static final int MAX_BATCHES = 50;
    private static final int MAX_ALLOCATION_KB = 512;
    private static final Duration MAX_PAUSE = Duration.ofMillis(200);

    @PostMapping("/pressure")
    public ResponseEntity<String> gcPressure(
            @RequestParam(name = "batches", defaultValue = "20") int batches,
            @RequestParam(name = "allocationKb", defaultValue = "256") int allocationKb,
            @RequestParam(name = "pauseMs", defaultValue = "50") long pauseMs)
            throws InterruptedException {
        int safeBatches = Math.min(batches, MAX_BATCHES);
        int safeAllocationKb = Math.min(allocationKb, MAX_ALLOCATION_KB);
        long safePauseMs = Math.min(pauseMs, MAX_PAUSE.toMillis());

        for (int i = 0; i < safeBatches; i++) {
            List<byte[]> allocations = new ArrayList<>();
            for (int j = 0; j < 10; j++) {
                allocations.add(new byte[safeAllocationKb * 1024]);
            }
            Thread.sleep(safePauseMs);
        }

        return ResponseEntity.ok("Generated allocation churn with " + safeBatches + " batches.");
    }
}
