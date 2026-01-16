package com.example.failuresim.failures.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates file descriptor leaks by opening streams and retaining them in memory.
 *
 * <p><strong>What fails:</strong> OS-level file descriptor limits are reached.</p>
 * <p><strong>Why in production:</strong> streams/sockets aren't closed in error paths.</p>
 * <p><strong>Symptoms:</strong> \"Too many open files\" errors, failing I/O operations.</p>
 * <p><strong>Metrics/logs:</strong> open file count, error logs, connection failures.</p>
 * <p><strong>Fix:</strong> use try-with-resources, enforce leak detection, and cap resources.</p>
 *
 * <p>We cap the number of open streams and allow manual cleanup to keep this safe.</p>
 */
@RestController
@RequestMapping("/fail/io")
public class IoLeakController {
    private static final int MAX_OPEN_STREAMS = 50;
    private static final List<InputStream> OPEN_STREAMS = new ArrayList<>();

    @PostMapping("/leak")
    public ResponseEntity<String> leakFileDescriptors(
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "false") boolean closeAll) throws IOException {
        if (closeAll) {
            for (InputStream stream : OPEN_STREAMS) {
                stream.close();
            }
            OPEN_STREAMS.clear();
            return ResponseEntity.ok("Closed all open streams.");
        }

        int safeCount = Math.min(count, MAX_OPEN_STREAMS - OPEN_STREAMS.size());
        File tempFile = File.createTempFile("failure-sim", ".txt");
        tempFile.deleteOnExit();

        for (int i = 0; i < safeCount; i++) {
            OPEN_STREAMS.add(new FileInputStream(tempFile));
        }

        return ResponseEntity.ok("Opened " + safeCount + " streams. Total open: " + OPEN_STREAMS.size());
    }
}
