package com.example.failuresim.failures.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DbPoolExhaustionController {

    /**
     * Failure: DB connection pool exhaustion (Hikari pool size = 3).
     * Why: Slow queries or leaked connections exhaust the pool; threads block waiting.
     * Symptom: Request timeouts, spikes in waiting threads, DB pool saturation.
     * Observability: Hikari metrics, thread dumps, DB wait time.
     * Fix: Reduce query latency, use timeouts, right-size pool, close connections.
     * Safety: Cap concurrent connections and hold time.
     */
    private static final Logger logger = LoggerFactory.getLogger(DbPoolExhaustionController.class);

    private static final int MAX_CONNECTIONS = 6;
    private static final long MAX_HOLD_MS = 5_000L;

    private final DataSource dataSource;
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONNECTIONS);

    public DbPoolExhaustionController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostMapping("/fail/db/slow")
    public ResponseEntity<Map<String, Object>> slowQueries(
            @RequestParam(defaultValue = "4") int connections,
            @RequestParam(defaultValue = "3000") long holdMs) throws InterruptedException {

        int safeConnections = Math.min(connections, MAX_CONNECTIONS);
        long safeHoldMs = Math.min(holdMs, MAX_HOLD_MS);

        CountDownLatch latch = new CountDownLatch(safeConnections);
        for (int i = 0; i < safeConnections; i++) {
            executor.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    Thread.sleep(safeHoldMs);
                } catch (SQLException ex) {
                    logger.warn("Failed to get connection: {}", ex.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(safeHoldMs + 500, TimeUnit.MILLISECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Held DB connections to exhaust the Hikari pool.");
        response.put("connections", safeConnections);
        response.put("holdDuration", Duration.ofMillis(safeHoldMs).toString());
        response.put("maxPoolSize", 3);
        return ResponseEntity.ok(response);
    }
}
