package com.example.failuresim.failures.security;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityMisconfigController {

    /**
     * Failure: Security misconfiguration (cookie SameSite/Secure mismatch).
     * Why: Modern browsers reject SameSite=None cookies without Secure.
     * Symptom: Auth works in staging but fails in production HTTPS/browser policies.
     * Observability: Auth error rates, browser console warnings, support tickets.
     * Fix: Set Secure with SameSite=None, validate cookies in pre-prod.
     */
    @GetMapping("/fail/security/cookie")
    public ResponseEntity<Map<String, Object>> cookieMisconfig(
            @RequestParam(defaultValue = "false") boolean secure,
            @RequestParam(defaultValue = "None") String sameSite) {

        ResponseCookie cookie = ResponseCookie.from("session_id", "demo")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Cookie attributes may break auth in modern browsers.");
        response.put("secure", secure);
        response.put("sameSite", sameSite);
        response.put("warning", "SameSite=None without Secure is rejected by browsers.");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }
}
