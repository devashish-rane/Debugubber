package com.example.failuresim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Intentionally no timeout configuration to demonstrate slow downstream impact.
        return new RestTemplate();
    }
}
