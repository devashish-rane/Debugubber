package com.example.failuresim.failures.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shows how treating LocalDateTime as UTC causes time shifts in production.
 *
 * <p><strong>What fails:</strong> time math shifts around DST or when deployments move regions.</p>
 * <p><strong>Why in production:</strong> LocalDateTime lacks timezone, leading to incorrect
 * conversions.</p>
 * <p><strong>Symptoms:</strong> off-by-hours timestamps, incorrect scheduling.</p>
 * <p><strong>Metrics/logs:</strong> timestamp anomalies, user reports around DST changes.</p>
 * <p><strong>Fix:</strong> use Instant/ZonedDateTime and store UTC in persistence.</p>
 */
@RestController
@RequestMapping("/fail/time")
public class TimeZoneBugController {

    @GetMapping("/zone")
    public ResponseEntity<String> timeZoneBug(
            @RequestParam(name = "localDateTime", defaultValue = "2024-03-10T01:30:00")
                    String localDateTime) {
        LocalDateTime input = LocalDateTime.parse(localDateTime);

        // Bug: assumes local time is UTC when it's actually system default.
        Instant incorrectInstant = input.toInstant(ZoneId.of("UTC").getRules().getOffset(input));
        ZonedDateTime correct = input.atZone(ZoneId.systemDefault());

        return ResponseEntity.ok("Incorrect UTC instant: " + incorrectInstant
                + " | Correct zoned time: " + correct);
    }
}
