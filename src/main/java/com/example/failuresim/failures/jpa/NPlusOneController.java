package com.example.failuresim.failures.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NPlusOneController {

    /**
     * Failure: N+1 query explosion due to lazy loading inside loops.
     * Why: ORM loads parent list then triggers a query per child collection.
     * Symptom: Slow endpoints, DB CPU spikes, excessive query count in logs.
     * Observability: SQL logs, APM traces, database query metrics.
     * Fix: Use fetch joins, batch fetching, DTO projections.
     */
    private final UserRepository userRepository;

    public NPlusOneController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/fail/jpa/n-plus-one")
    public ResponseEntity<Map<String, Object>> nPlusOne() {
        // Fetch users (1 query) then access lazy orders (N additional queries).
        List<UserEntity> users = userRepository.findAll();

        List<Map<String, Object>> payload = users.stream()
                .map(user -> {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("id", user.getId());
                    userData.put("name", user.getName());
                    userData.put("orders", user.getOrders().stream()
                            .map(OrderEntity::getDescription)
                            .collect(Collectors.toList()));
                    return userData;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Triggered N+1 query pattern by accessing lazy collection.");
        response.put("users", payload);
        return ResponseEntity.ok(response);
    }
}
