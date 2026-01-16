package com.example.failuresim.failures.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates how synchronous logging can dominate request time and I/O.
 *
 * <p><strong>What fails:</strong> logging becomes the bottleneck instead of business logic.</p>
 * <p><strong>Why in production:</strong> excessive INFO logs on hot paths overwhelm disks and
 * logging backends, slowing request processing.</p>
 * <p><strong>Symptoms:</strong> high latency, log pipeline saturation, CPU idle.</p>
 * <p><strong>Metrics/logs:</strong> log throughput, I/O wait, request duration.</p>
 * <p><strong>Fix:</strong> reduce log volume, sample logs, or move to async logging.</p>
 */
@RestController
@RequestMapping("/fail/logging")
public class LoggingSpamController {
    private static final Logger log = LoggerFactory.getLogger(LoggingSpamController.class);
    private static final int MAX_LINES = 200;

    @PostMapping("/spam")
    public ResponseEntity<String> spamLogs(
            @RequestParam(name = "lines", defaultValue = "50") int lines) {
        int safeLines = Math.min(lines, MAX_LINES);
        for (int i = 1; i <= safeLines; i++) {
            log.info("Logging overload simulation line {}/{}", i, safeLines);
        }
        return ResponseEntity.ok("Logged " + safeLines + " lines.");
    }
}
