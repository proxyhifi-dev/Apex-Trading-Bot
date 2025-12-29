package com.apex.backend.repository;

import com.apex.backend.model.FailedOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FailedOperationRepository extends JpaRepository<FailedOperation, Long> {
    List<FailedOperation> findByResolvedFalse();
}