package com.example.failuresim.failures.threads;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ThreadPoolExhaustionController {

    /**
     * Failure: Thread pool exhaustion with blocked tasks.
     * Why: Small pool + blocking IO/starvation causes request queue buildup.
     * Symptom: Hanging requests, low CPU, growing queue.
     * Observability: Thread pool metrics, request latency, queue depth.
     * Fix: Use timeouts, larger pools, async IO, backpressure.
     * Safety: Task count and block duration are capped.
     */
    /**
     * Small pool + queue to show how requests can stack up even when CPU is idle.
     */
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2));

    private static final int MAX_TASKS = 10;
    private static final long MAX_BLOCK_MS = 5_000L;

    @PostMapping("/fail/threads/block")
    public ResponseEntity<Map<String, Object>> blockThreads(
            @RequestParam(defaultValue = "4") int tasks,
            @RequestParam(defaultValue = "3000") long blockMs) throws InterruptedException {

        int safeTasks = Math.min(tasks, MAX_TASKS);
        long safeBlockMs = Math.min(blockMs, MAX_BLOCK_MS);

        CountDownLatch latch = new CountDownLatch(safeTasks);
        int rejected = 0;
        for (int i = 0; i < safeTasks; i++) {
            try {
                EXECUTOR.execute(() -> {
                    try {
                        Thread.sleep(safeBlockMs);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (RejectedExecutionException ex) {
                rejected++;
                latch.countDown();
            }
        }

        // Wait for tasks to finish but never longer than the configured cap.
        latch.await(safeBlockMs + 500, TimeUnit.MILLISECONDS);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Submitted blocking tasks to a tiny thread pool.");
        response.put("requestedTasks", safeTasks);
        response.put("blockDuration", Duration.ofMillis(safeBlockMs).toString());
        response.put("rejected", rejected);
        response.put("poolSize", EXECUTOR.getPoolSize());
        response.put("queueSize", EXECUTOR.getQueue().size());
        return ResponseEntity.ok(response);
    }
}
