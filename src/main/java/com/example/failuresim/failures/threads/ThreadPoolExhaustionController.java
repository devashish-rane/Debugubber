package com.example.failuresim.failures.threads;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates thread pool exhaustion by queuing blocking tasks on a tiny executor.
 *
 * <p><strong>What fails:</strong> request threads are blocked waiting on a saturated pool.</p>
 * <p><strong>Why in production:</strong> slow downstream calls or blocking I/O on a fixed-size
 * executor leads to tasks piling up behind a few busy threads.</p>
 * <p><strong>Symptoms:</strong> high request latency, thread pool queue growth, idle CPU.</p>
 * <p><strong>Metrics/logs:</strong> thread pool queue depth, request latency p95/p99, thread dumps.</p>
 * <p><strong>Fix:</strong> isolate blocking work, increase pool sizes carefully, or add timeouts.</p>
 */
@RestController
@RequestMapping("/fail/threads")
public class ThreadPoolExhaustionController {
    private static final int MAX_TASKS = 20;
    private static final Duration MAX_BLOCK_TIME = Duration.ofSeconds(10);

    private final ExecutorService smallBlockingExecutor;

    public ThreadPoolExhaustionController(ExecutorService smallBlockingExecutor) {
        this.smallBlockingExecutor = smallBlockingExecutor;
    }

    @PostMapping("/block")
    public ResponseEntity<String> blockThreads(
            @RequestParam(name = "tasks", defaultValue = "5") int tasks,
            @RequestParam(name = "blockMs", defaultValue = "2000") long blockMs)
            throws Exception {
        int safeTasks = Math.min(tasks, MAX_TASKS);
        long safeBlockMs = Math.min(blockMs, MAX_BLOCK_TIME.toMillis());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < safeTasks; i++) {
            futures.add(smallBlockingExecutor.submit(() -> {
                try {
                    Thread.sleep(safeBlockMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        return ResponseEntity.ok("Blocked " + safeTasks + " tasks for " + safeBlockMs + "ms.");
    }
}
