package com.example.failuresim.failures.jpa;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triggers N+1 query behavior by lazily fetching orders for each customer.
 *
 * <p><strong>What fails:</strong> one query per parent entity causes query explosion.</p>
 * <p><strong>Why in production:</strong> lazy loading in a loop triggers extra queries.</p>
 * <p><strong>Symptoms:</strong> high DB load, slow endpoints, poor scalability.</p>
 * <p><strong>Metrics/logs:</strong> SQL logs show repeated queries, DB CPU/IO spikes.</p>
 * <p><strong>Fix:</strong> use fetch joins, batch size, or DTO projections.</p>
 */
@RestController
@RequestMapping("/fail/jpa")
public class NPlusOneController {
    private final CustomerRepository customerRepository;

    public NPlusOneController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @GetMapping("/n-plus-one")
    public ResponseEntity<String> nPlusOne() {
        List<Customer> customers = customerRepository.findAll();

        // Accessing lazy collections triggers one query per customer (N+1).
        int totalOrders = customers.stream()
                .mapToInt(customer -> customer.getOrders().size())
                .sum();

        return ResponseEntity.ok("Loaded " + customers.size() + " customers and "
                + totalOrders + " orders (check SQL logs for N+1 queries)." );
    }
}
