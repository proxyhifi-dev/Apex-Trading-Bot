package com.apex.backend.repository;

import com.apex.backend.model.PaperOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaperOrderRepository extends JpaRepository<PaperOrder, Long> {
    List<PaperOrder> findByStatus(String status);
}
