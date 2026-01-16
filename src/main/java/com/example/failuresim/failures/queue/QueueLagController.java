package com.example.failuresim.failures.queue;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates consumer lag by enqueueing more work than the single consumer can drain.
 *
 * <p><strong>What fails:</strong> backlog grows, delaying processing and user-visible actions.</p>
 * <p><strong>Why in production:</strong> producer throughput exceeds consumer capacity.</p>
 * <p><strong>Symptoms:</strong> delayed processing, rising queue depth, uneven throughput.</p>
 * <p><strong>Metrics/logs:</strong> queue depth, processing lag, consumer CPU utilization.</p>
 * <p><strong>Fix:</strong> scale consumers, add backpressure, or optimize processing.</p>
 */
@RestController
@RequestMapping("/fail/queue")
public class QueueLagController {
    private static final int MAX_ITEMS = 200;

    private final BlockingQueue<String> workQueue;

    public QueueLagController(BlockingQueue<String> workQueue) {
        this.workQueue = workQueue;
    }

    @PostMapping("/lag")
    public ResponseEntity<String> queueLag(
            @RequestParam(name = "items", defaultValue = "50") int items) {
        int safeItems = Math.min(items, MAX_ITEMS);
        int accepted = 0;

        for (int i = 0; i < safeItems; i++) {
            boolean offered = workQueue.offer("job-" + UUID.randomUUID());
            if (offered) {
                accepted++;
            }
        }

        return ResponseEntity.ok("Enqueued " + accepted + " items. Current backlog: " + workQueue.size());
    }
}
