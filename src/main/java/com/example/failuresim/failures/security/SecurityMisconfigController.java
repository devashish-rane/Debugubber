package com.example.failuresim.failures.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates cookie misconfiguration that only fails in HTTPS-only production setups.
 *
 * <p><strong>What fails:</strong> browsers reject the cookie in production, breaking auth.</p>
 * <p><strong>Why in production:</strong> SameSite=None requires Secure=true in modern browsers.</p>
 * <p><strong>Symptoms:</strong> users can't stay logged in only in prod (HTTPS).</p>
 * <p><strong>Metrics/logs:</strong> auth failure rates, missing session cookies.</p>
 * <p><strong>Fix:</strong> set Secure when SameSite=None or adjust SameSite policy.</p>
 */
@RestController
@RequestMapping("/fail/security")
public class SecurityMisconfigController {

    @GetMapping("/cookie")
    public ResponseEntity<String> cookieMisconfig() {
        // BUG: SameSite=None requires Secure, but we omit Secure to simulate prod-only login issues.
        ResponseCookie cookie = ResponseCookie.from("session", "demo")
                .httpOnly(true)
                .sameSite("None")
                .secure(false)
                .path("/")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body("Set cookie with SameSite=None without Secure flag.");
    }
}
