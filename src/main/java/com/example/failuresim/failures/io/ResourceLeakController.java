package com.example.failuresim.failures.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResourceLeakController {

    /**
     * Failure: File descriptor/resource leak by leaving streams open.
     * Why: Missing try-with-resources or finally blocks in IO paths.
     * Symptom: "Too many open files", failing IO, degraded host stability.
     * Observability: OS open FD counts, error logs, IO exceptions.
     * Fix: Ensure close in finally/try-with-resources, monitor FD usage.
     * Safety: Hard cap and cleanup option prevent host damage.
     */
    /**
     * List of open streams kept intentionally to simulate leaks.
     * HARD CAP prevents exhausting file descriptors on a laptop.
     */
    private static final List<FileInputStream> OPEN_STREAMS = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_OPEN = 50;

    @PostMapping("/fail/io/leak")
    public ResponseEntity<Map<String, Object>> leakFileDescriptors(
            @RequestParam(defaultValue = "5") int open,
            @RequestParam(defaultValue = "false") boolean cleanup) throws IOException {

        if (cleanup) {
            closeAll();
        }

        int safeOpen = Math.min(open, MAX_OPEN - OPEN_STREAMS.size());
        File temp = File.createTempFile("leak-demo", ".tmp");
        temp.deleteOnExit();

        for (int i = 0; i < safeOpen; i++) {
            // Intentionally NOT closing to simulate file descriptor leak.
            OPEN_STREAMS.add(new FileInputStream(temp));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Opened file streams and left them unclosed (capped). Use cleanup=true to close.");
        response.put("openedThisCall", safeOpen);
        response.put("openStreams", OPEN_STREAMS.size());
        response.put("maxOpen", MAX_OPEN);
        return ResponseEntity.ok(response);
    }

    private void closeAll() throws IOException {
        for (FileInputStream stream : OPEN_STREAMS) {
            stream.close();
        }
        OPEN_STREAMS.clear();
    }
}
