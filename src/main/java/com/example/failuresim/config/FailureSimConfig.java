package com.example.failuresim.config;

import java.time.Clock;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Shared configuration for the simulator.
 *
 * <p>Centralizing these beans keeps resource limits explicit and makes it easier
 * for developers to see global constraints used across scenarios.</p>
 */
@Configuration
public class FailureSimConfig {

    /**
     * Small fixed thread pool to intentionally trigger thread pool exhaustion.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService smallBlockingExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    /**
     * RestTemplate without timeouts to demonstrate how downstream calls can hang.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    /**
     * Shared in-memory queue for async lag simulation.
     */
    @Bean
    public BlockingQueue<String> workQueue() {
        return new LinkedBlockingQueue<>(1000);
    }

    @Bean
    public ApplicationRunner h2SleepAliasInitializer(JdbcTemplate jdbcTemplate) {
        return args -> jdbcTemplate.execute(
                "CREATE ALIAS IF NOT EXISTS DB_SLEEP FOR \"java.lang.Thread.sleep\"");
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
