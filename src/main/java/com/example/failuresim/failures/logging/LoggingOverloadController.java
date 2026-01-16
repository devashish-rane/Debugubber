package com.example.failuresim.failures.logging;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoggingOverloadController {

    /**
     * Failure: Logging overload and sync IO pressure.
     * Why: Large log lines in tight loops block threads and flood disks.
     * Symptom: Request latency spikes, log pipeline lag, high IO wait.
     * Observability: Log throughput, disk IO, application latency.
     * Fix: Use structured logging, rate limit, async appenders, log levels.
     * Safety: Log volume capped.
     */
    private static final Logger logger = LoggerFactory.getLogger(LoggingOverloadController.class);

    private static final int MAX_LINES = 200;
    private static final int MAX_LINE_KB = 8;

    @PostMapping("/fail/logging/spam")
    public ResponseEntity<Map<String, Object>> spamLogs(
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(defaultValue = "4") int lineKb) {

        int safeLines = Math.min(lines, MAX_LINES);
        int safeLineKb = Math.min(lineKb, MAX_LINE_KB);
        String payload = "x".repeat(safeLineKb * 1024);

        for (int i = 0; i < safeLines; i++) {
            logger.info("LOG-SPAM {} {}", i + 1, payload);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Logged large payloads in a tight loop.");
        response.put("lines", safeLines);
        response.put("lineKb", safeLineKb);
        return ResponseEntity.ok(response);
    }
}
