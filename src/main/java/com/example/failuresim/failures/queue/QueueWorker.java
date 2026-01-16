package com.example.failuresim.failures.queue;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Background worker that drains the in-memory queue slowly.
 *
 * <p>This is intentionally slow to highlight backlog growth. It is bounded by
 * queue size and sleep time, so it is safe for local execution.</p>
 */
@Component
public class QueueWorker {
    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);
    private static final Duration PROCESSING_TIME = Duration.ofMillis(250);

    private final BlockingQueue<String> workQueue;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public QueueWorker(BlockingQueue<String> workQueue) {
        this.workQueue = workQueue;
    }

    @PostConstruct
    public void start() {
        worker.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String job = workQueue.take();
                    Thread.sleep(PROCESSING_TIME.toMillis());
                    log.info("Processed job: {}", job);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }
}
