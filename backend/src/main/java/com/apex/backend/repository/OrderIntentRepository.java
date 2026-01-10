package com.apex.backend.repository;

import com.apex.backend.model.OrderIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderIntentRepository extends JpaRepository<OrderIntent, Long> {
    Optional<OrderIntent> findByClientOrderId(String clientOrderId);
}
