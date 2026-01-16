package com.example.failuresim.failures.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaleCacheController {

    /**
     * Failure: Cache poisoning / stale cache from incorrect keys + mutable values.
     * Why: Cache key ignores locale and cached object is mutated in-place.
     * Symptom: Users see wrong language/content, inconsistent responses.
     * Observability: Cache hit ratios, response mismatches, debug logs.
     * Fix: Use correct cache keys, immutable values, cache invalidation.
     */
    /**
     * Keyed only by userId to demonstrate an incorrect cache key.
     * Locale-specific data will be overwritten or returned incorrectly.
     */
    private static final Map<String, CachedProfile> CACHE = new ConcurrentHashMap<>();

    @GetMapping("/fail/cache/stale")
    public ResponseEntity<Map<String, Object>> staleCache(
            @RequestParam(defaultValue = "42") String userId,
            @RequestParam(defaultValue = "en-US") String locale) {

        CachedProfile cached = CACHE.computeIfAbsent(userId, key -> new CachedProfile());
        cached.setLocale(locale);
        cached.setGreeting(locale.toLowerCase().startsWith("en") ? "Hello" : "Hola");

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Cache key ignores locale so stale data leaks across users/locales.");
        response.put("userId", userId);
        response.put("requestedLocale", locale);
        response.put("cachedLocale", cached.getLocale());
        response.put("cachedGreeting", cached.getGreeting());
        return ResponseEntity.ok(response);
    }

    private static class CachedProfile {
        private String locale;
        private String greeting;

        public String getLocale() {
            return locale;
        }

        public void setLocale(String locale) {
            this.locale = locale;
        }

        public String getGreeting() {
            return greeting;
        }

        public void setGreeting(String greeting) {
            this.greeting = greeting;
        }
    }
}
