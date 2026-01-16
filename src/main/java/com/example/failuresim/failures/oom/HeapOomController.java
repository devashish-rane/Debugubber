package com.example.failuresim.failures.oom;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeapOomController {

    /**
     * Failure: Heap OutOfMemoryError via a static map leak.
     * Why: Caches or static collections with no eviction retain objects forever.
     * Symptom: Rising heap usage, GC thrash, eventual OOM.
     * Observability: Heap usage dashboards, GC logs, heap dumps.
     * Fix: Add eviction/TTL, remove static retention, use bounded caches.
     * Safety: Entry count and size are capped to avoid destabilizing laptops.
     */
    /**
     * Static map that intentionally retains references to simulate a memory leak.
     * In production this often happens when caches or static collections never evict.
     */
    private static final Map<Integer, byte[]> LEAK = Collections.synchronizedMap(new HashMap<>());
    private static final AtomicInteger KEY_COUNTER = new AtomicInteger(0);

    private static final int MAX_ENTRIES = 200;
    private static final int MAX_ENTRY_KB = 256;

    @PostMapping("/fail/oom/heap")
    public ResponseEntity<Map<String, Object>> leakHeap(
            @RequestParam(defaultValue = "10") int entries,
            @RequestParam(defaultValue = "64") int entryKb) {

        int safeEntries = Math.min(entries, MAX_ENTRIES);
        int safeEntryKb = Math.min(entryKb, MAX_ENTRY_KB);

        for (int i = 0; i < safeEntries; i++) {
            // Each entry allocates a byte[] and is stored in the static map.
            LEAK.put(KEY_COUNTER.incrementAndGet(), new byte[safeEntryKb * 1024]);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Allocated leak entries. Restart app to clear.");
        response.put("storedEntries", LEAK.size());
        response.put("entryKb", safeEntryKb);
        response.put("cappedEntries", safeEntries);
        response.put("maxEntries", MAX_ENTRIES);
        return ResponseEntity.ok(response);
    }
}
