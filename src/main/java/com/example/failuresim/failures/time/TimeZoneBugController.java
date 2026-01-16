package com.example.failuresim.failures.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TimeZoneBugController {

    /**
     * Failure: Timezone bug when using LocalDateTime instead of Instant/ZonedDateTime.
     * Why: LocalDateTime drops timezone offset, so conversions reapply the wrong zone.
     * Symptom: Off-by-hours errors, DST bugs, incorrect scheduling windows.
     * Observability: Time-based anomalies, audit logs, customer reports.
     * Fix: Store Instant, use ZonedDateTime with explicit zones.
     */
    @GetMapping("/fail/time/zone")
    public ResponseEntity<Map<String, Object>> timeZoneBug(
            @RequestParam(defaultValue = "America/Los_Angeles") String zone) {

        ZoneId zoneId = ZoneId.of(zone);
        Instant now = Instant.now();

        // BUG: LocalDateTime drops the zone, so converting back assumes JVM default.
        LocalDateTime localDateTime = LocalDateTime.ofInstant(now, zoneId);
        ZonedDateTime wrongReconstructed = localDateTime.atZone(ZoneId.systemDefault());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "LocalDateTime loses timezone context, causing DST/zone bugs.");
        response.put("instant", now.toString());
        response.put("originalZone", zoneId.toString());
        response.put("localDateTime", localDateTime.toString());
        response.put("wrongReconstructed", wrongReconstructed.toString());
        response.put("correctReconstructed", ZonedDateTime.ofInstant(now, zoneId).toString());
        return ResponseEntity.ok(response);
    }
}
