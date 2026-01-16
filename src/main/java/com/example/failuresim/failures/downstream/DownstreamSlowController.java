package com.example.failuresim.failures.downstream;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Simulates slow downstream dependency calls and demonstrates the impact of missing timeouts.
 *
 * <p><strong>What fails:</strong> request thread is blocked waiting on a slow dependency.</p>
 * <p><strong>Why in production:</strong> client calls without timeouts can hang indefinitely,
 * amplifying a downstream slowdown.</p>
 * <p><strong>Symptoms:</strong> hanging requests, rising latency, thread pool saturation.</p>
 * <p><strong>Metrics/logs:</strong> client latency histograms, thread dumps, downstream SLA.</p>
 * <p><strong>Fix:</strong> set timeouts, use circuit breakers, bulkheads, and fallbacks.</p>
 */
@RestController
@RequestMapping("/fail/downstream")
public class DownstreamSlowController {
    private static final Duration MAX_DELAY = Duration.ofSeconds(10);

    private final RestTemplate restTemplate;
    private final int serverPort;

    public DownstreamSlowController(RestTemplate restTemplate,
                                    @Value("${server.port:8080}") int serverPort) {
        this.restTemplate = restTemplate;
        this.serverPort = serverPort;
    }

    @GetMapping("/slow")
    public ResponseEntity<String> callSlowDependency(
            @RequestParam(name = "delayMs", defaultValue = "2000") long delayMs) {
        long safeDelayMs = Math.min(delayMs, MAX_DELAY.toMillis());
        String url = "http://localhost:" + serverPort + "/internal/downstream/slow?delayMs=" + safeDelayMs;

        // No timeout configured: call will block for the full delay.
        String result = restTemplate.getForObject(url, String.class);
        return ResponseEntity.ok("Downstream response: " + result);
    }
}
