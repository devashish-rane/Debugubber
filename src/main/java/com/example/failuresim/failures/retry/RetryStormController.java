package com.example.failuresim.failures.retry;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Demonstrates retry amplification, a common cause of self-induced load spikes.
 *
 * <p><strong>What fails:</strong> a failing downstream triggers retries, multiplying traffic.</p>
 * <p><strong>Why in production:</strong> naive retry logic fans out calls and makes outages worse.</p>
 * <p><strong>Symptoms:</strong> sudden QPS spikes, growing error rates, downstream overload.</p>
 * <p><strong>Metrics/logs:</strong> retry counts, request volume, downstream latency and errors.</p>
 * <p><strong>Fix:</strong> cap retries, jitter backoff, and use circuit breakers.</p>
 */
@RestController
@RequestMapping("/fail/retry")
public class RetryStormController {
    private static final Logger log = LoggerFactory.getLogger(RetryStormController.class);
    private static final int MAX_RETRIES = 3;
    private static final Duration MAX_DELAY = Duration.ofSeconds(2);

    private final RestTemplate restTemplate;
    private final int serverPort;

    public RetryStormController(RestTemplate restTemplate,
                                @Value("${server.port:8080}") int serverPort) {
        this.restTemplate = restTemplate;
        this.serverPort = serverPort;
    }

    @PostMapping("/storm")
    public ResponseEntity<String> retryStorm(
            @RequestParam(name = "retries", defaultValue = "3") int retries,
            @RequestParam(name = "delayMs", defaultValue = "250") long delayMs) {
        int safeRetries = Math.min(retries, MAX_RETRIES);
        long safeDelayMs = Math.min(delayMs, MAX_DELAY.toMillis());
        String url = "http://localhost:" + serverPort + "/internal/downstream/fail";

        int attempts = 0;
        while (attempts <= safeRetries) {
            attempts++;
            try {
                restTemplate.getForObject(url, String.class);
                break;
            } catch (Exception ex) {
                log.warn("Retry attempt {} failed.", attempts, ex);
                try {
                    Thread.sleep(safeDelayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return ResponseEntity.ok("Completed " + attempts + " attempts (max retries " + safeRetries + ").");
    }
}
