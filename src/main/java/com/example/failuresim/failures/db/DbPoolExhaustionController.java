package com.example.failuresim.failures.db;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates exhausting the JDBC connection pool by running slow queries concurrently.
 *
 * <p><strong>What fails:</strong> request threads wait for a DB connection from Hikari.</p>
 * <p><strong>Why in production:</strong> small pool + slow queries + high concurrency starves
 * threads and causes a backlog.</p>
 * <p><strong>Symptoms:</strong> requests stall, thread dumps show threads waiting on pool.</p>
 * <p><strong>Metrics/logs:</strong> HikariCP active/idle connections, slow query logs.</p>
 * <p><strong>Fix:</strong> tune pool size, reduce query latency, add timeouts, and backpressure.</p>
 */
@RestController
@RequestMapping("/fail/db")
public class DbPoolExhaustionController {
    private static final int MAX_CONCURRENT = 10;
    private static final Duration MAX_DELAY = Duration.ofSeconds(5);

    private final JdbcTemplate jdbcTemplate;

    public DbPoolExhaustionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/slow")
    public ResponseEntity<String> slowQueries(
            @RequestParam(name = "concurrent", defaultValue = "5") int concurrent,
            @RequestParam(name = "delayMs", defaultValue = "2000") long delayMs) {
        int safeConcurrent = Math.min(concurrent, MAX_CONCURRENT);
        long safeDelayMs = Math.min(delayMs, MAX_DELAY.toMillis());

        ExecutorService executor = Executors.newFixedThreadPool(safeConcurrent);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < safeConcurrent; i++) {
            futures.add(CompletableFuture.runAsync(() ->
                    jdbcTemplate.execute("CALL SLEEP(" + safeDelayMs + ")"), executor));
        }

        futures.forEach(CompletableFuture::join);
        executor.shutdown();

        return ResponseEntity.ok("Executed " + safeConcurrent + " slow DB calls with "
                + safeDelayMs + "ms delay.");
    }
}
