package com.example.failuresim.failures.downstream;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class SlowDownstreamController {

    /**
     * Failure: Slow downstream dependency without timeouts.
     * Why: Blocking HTTP calls consume server threads when downstream is slow.
     * Symptom: Elevated latency, thread pool exhaustion, cascading failures.
     * Observability: Downstream latency metrics, thread utilization, traces.
     * Fix: Set timeouts, circuit breakers, bulkheads, fallbacks.
     * Safety: Delay is capped.
     */
    private static final long MAX_DELAY_MS = 5_000L;

    private final RestTemplate restTemplate;

    public SlowDownstreamController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/downstream/mock")
    public ResponseEntity<Map<String, Object>> mockDownstream(
            @RequestParam(defaultValue = "2000") long delayMs) throws InterruptedException {
        long safeDelay = Math.min(delayMs, MAX_DELAY_MS);
        Thread.sleep(safeDelay);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Downstream responded after delay.");
        response.put("delay", Duration.ofMillis(safeDelay).toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/fail/downstream/slow")
    public ResponseEntity<Map<String, Object>> callSlowDownstream(
            @RequestParam(defaultValue = "2000") long delayMs) {
        long safeDelay = Math.min(delayMs, MAX_DELAY_MS);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/downstream/mock")
                .queryParam("delayMs", safeDelay)
                .toUriString();

        // No timeout configured; the calling thread will block for the full delay.
        Map<?, ?> downstream = restTemplate.getForObject(url, Map.class);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Caller waited on slow downstream without a timeout.");
        response.put("downstream", downstream);
        return ResponseEntity.ok(response);
    }
}
