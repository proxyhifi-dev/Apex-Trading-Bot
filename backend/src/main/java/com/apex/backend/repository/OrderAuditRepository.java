package com.apex.backend.repository;

import com.apex.backend.model.OrderAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderAuditRepository extends JpaRepository<OrderAudit, Long> {
}
