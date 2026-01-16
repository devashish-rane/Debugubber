package com.example.failuresim.failures.gc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GcPressureController {

    /**
     * Failure: GC pressure without OOM.
     * Why: High allocation rates create frequent GC pauses.
     * Symptom: p99 latency spikes, increased GC time, CPU jitter.
     * Observability: GC logs, allocation rate metrics, latency histograms.
     * Fix: Reduce allocations, reuse buffers, tune GC, batch work.
     * Safety: Total allocated memory is capped and cleared.
     */
    private static final int MAX_TOTAL_MB = 64;
    private static final int MAX_CHUNK_KB = 512;

    @PostMapping("/fail/gc/pressure")
    public ResponseEntity<Map<String, Object>> gcPressure(
            @RequestParam(defaultValue = "32") int totalMb,
            @RequestParam(defaultValue = "256") int chunkKb) {

        int safeTotalMb = Math.min(totalMb, MAX_TOTAL_MB);
        int safeChunkKb = Math.min(chunkKb, MAX_CHUNK_KB);

        int totalBytes = safeTotalMb * 1024 * 1024;
        int chunkBytes = safeChunkKb * 1024;
        int chunks = Math.max(1, totalBytes / chunkBytes);

        List<byte[]> allocations = new ArrayList<>(chunks);
        Instant start = Instant.now();
        for (int i = 0; i < chunks; i++) {
            allocations.add(new byte[chunkBytes]);
        }
        // Drop references to force GC pressure without OOM.
        allocations.clear();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Allocated and released memory to trigger GC activity.");
        response.put("totalAllocatedMb", safeTotalMb);
        response.put("chunkKb", safeChunkKb);
        response.put("duration", Duration.between(start, Instant.now()).toString());
        return ResponseEntity.ok(response);
    }
}
