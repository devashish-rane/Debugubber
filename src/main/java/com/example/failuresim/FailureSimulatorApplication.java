package com.example.failuresim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Production Failure Simulator.
 *
 * <p>This application intentionally exposes endpoints that trigger common production
 * failure modes in a controlled, bounded, and safe manner. Nothing runs automatically
 * on startup and every failure is triggered explicitly by an HTTP request.</p>
 */
@SpringBootApplication
public class FailureSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FailureSimulatorApplication.class, args);
    }
}
