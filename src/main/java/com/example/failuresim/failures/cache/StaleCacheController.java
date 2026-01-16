package com.example.failuresim.failures.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates cache poisoning due to incorrect cache key selection.
 *
 * <p><strong>What fails:</strong> cached data is returned for the wrong region/segment.</p>
 * <p><strong>Why in production:</strong> cache keys omit critical dimensions (region, tenant, locale).</p>
 * <p><strong>Symptoms:</strong> users see incorrect data, stale results, or data leaks.</p>
 * <p><strong>Metrics/logs:</strong> cache hit anomalies, user reports, data correctness alerts.</p>
 * <p><strong>Fix:</strong> include all partitioning fields in the cache key and avoid mutable values.</p>
 */
@RestController
@RequestMapping("/fail/cache")
public class StaleCacheController {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @GetMapping("/stale")
    public ResponseEntity<String> staleCache(
            @RequestParam String userId,
            @RequestParam(defaultValue = "us-east") String region,
            @RequestParam(defaultValue = "blue") String segment) {
        // BUG: cache key ignores region and segment, causing cross-region contamination.
        String cacheKey = userId;
        String value = cache.computeIfAbsent(cacheKey,
                key -> "segment=" + segment + ", region=" + region);

        return ResponseEntity.ok("Cache key=" + cacheKey + ", value=" + value);
    }
}
