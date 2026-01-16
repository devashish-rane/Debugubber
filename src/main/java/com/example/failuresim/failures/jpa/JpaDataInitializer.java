package com.example.failuresim.failures.jpa;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with a few customers and orders so the N+1 behavior is visible.
 */
@Component
public class JpaDataInitializer implements CommandLineRunner {
    private final CustomerRepository customerRepository;

    public JpaDataInitializer(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            return;
        }

        for (int i = 1; i <= 5; i++) {
            Customer customer = new Customer("Customer " + i);
            for (int j = 1; j <= 3; j++) {
                customer.addOrder(new PurchaseOrder("Order " + i + "-" + j));
            }
            customerRepository.save(customer);
        }
    }
}
