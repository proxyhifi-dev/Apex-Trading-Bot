package com.apex.backend.repository;

import com.apex.backend.model.OrderIntent;
import com.apex.backend.model.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderIntentRepository extends JpaRepository<OrderIntent, Long> {
    Optional<OrderIntent> findByClientOrderId(String clientOrderId);
    
    List<OrderIntent> findByUserIdAndOrderStateIn(Long userId, List<OrderState> states);

    long countByOrderStateIn(List<OrderState> states);
}
