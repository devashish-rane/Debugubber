package com.example.failuresim.failures.queue;

import java.time.Duration;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueueLagController {

    /**
     * Failure: Async consumer lag and backlog growth.
     * Why: Producer rate exceeds consumer throughput; queue grows silently.
     * Symptom: Delayed processing, stale data, eventual memory pressure.
     * Observability: Queue depth, consumer lag, processing latency.
     * Fix: Autoscale consumers, apply backpressure, tune batch size.
     * Safety: Enqueue and processing caps prevent unbounded growth.
     */
    private static final Deque<String> QUEUE = new ConcurrentLinkedDeque<>();
    private static final int MAX_ENQUEUE = 50;
    private static final int MAX_BACKLOG = 200;
    private static final int MAX_PROCESS = 10;
    private static final long MAX_PROCESS_MS = 500L;

    @PostMapping("/fail/queue/lag")
    public ResponseEntity<Map<String, Object>> queueLag(
            @RequestParam(defaultValue = "20") int enqueue,
            @RequestParam(defaultValue = "5") int process,
            @RequestParam(defaultValue = "200") long processMs) throws InterruptedException {

        int safeEnqueue = Math.min(enqueue, MAX_ENQUEUE);
        int safeProcess = Math.min(process, MAX_PROCESS);
        long safeProcessMs = Math.min(processMs, MAX_PROCESS_MS);

        int enqueued = 0;
        for (int i = 0; i < safeEnqueue; i++) {
            if (QUEUE.size() >= MAX_BACKLOG) {
                break;
            }
            QUEUE.add("job-" + System.nanoTime());
            enqueued++;
        }

        int processed = 0;
        for (int i = 0; i < safeProcess && !QUEUE.isEmpty(); i++) {
            QUEUE.poll();
            processed++;
            Thread.sleep(safeProcessMs);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Simulated consumer lag by enqueueing faster than processing.");
        response.put("enqueue", enqueued);
        response.put("processed", processed);
        response.put("processingDelay", Duration.ofMillis(safeProcessMs).toString());
        response.put("backlog", QUEUE.size());
        response.put("maxBacklog", MAX_BACKLOG);
        return ResponseEntity.ok(response);
    }
}
