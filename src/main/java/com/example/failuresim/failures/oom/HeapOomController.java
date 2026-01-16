package com.example.failuresim.failures.oom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates a heap OutOfMemoryError using a static map-based leak.
 *
 * <p><strong>What fails:</strong> the heap fills up because we keep references in a static map.</p>
 * <p><strong>Why in production:</strong> cache leaks, unbounded memoization, or missing eviction
 * cause objects to be retained long after they should be GC'd.</p>
 * <p><strong>Symptoms:</strong> rising heap usage, increased GC time, eventual OOM kill or crash.</p>
 * <p><strong>Metrics/logs:</strong> JVM heap usage, GC pause times, and OOM stack traces.</p>
 * <p><strong>Fix:</strong> introduce eviction, size caps, or use weak references with monitoring.</p>
 *
 * <p>Safety: entry count and entry size are capped to keep this safe for laptops.</p>
 */
@RestController
@RequestMapping("/fail/oom")
public class HeapOomController {
    private static final Map<Integer, byte[]> LEAK = new ConcurrentHashMap<>();
    private static final int MAX_ENTRIES = 2000;
    private static final int MAX_ENTRY_KB = 256;

    @PostMapping("/heap")
    public ResponseEntity<String> leakHeap(
            @RequestParam(name = "entries", defaultValue = "50") int entries,
            @RequestParam(name = "entryKb", defaultValue = "64") int entryKb) {
        int safeEntries = Math.min(entries, MAX_ENTRIES);
        int safeEntryKb = Math.min(entryKb, MAX_ENTRY_KB);

        int allowedAdds = Math.min(safeEntries, MAX_ENTRIES - LEAK.size());
        for (int i = 0; i < allowedAdds; i++) {
            LEAK.put(LEAK.size() + 1, new byte[safeEntryKb * 1024]);
        }

        String message = "Leaked " + allowedAdds + " entries (~" + safeEntryKb
                + "KB each). Total retained entries: " + LEAK.size();
        return ResponseEntity.ok(message);
    }
}
