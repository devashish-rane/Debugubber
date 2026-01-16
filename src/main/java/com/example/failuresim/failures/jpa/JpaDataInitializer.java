package com.example.failuresim.failures.jpa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class JpaDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(JpaDataInitializer.class);

    private final UserRepository userRepository;

    public JpaDataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        for (int i = 1; i <= 5; i++) {
            UserEntity user = new UserEntity("user-" + i);
            for (int j = 1; j <= 3; j++) {
                user.addOrder(new OrderEntity("order-" + i + "-" + j));
            }
            userRepository.save(user);
        }
        logger.info("Seeded sample users/orders for N+1 demo.");
    }
}
