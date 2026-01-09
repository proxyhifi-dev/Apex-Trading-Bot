package com.apex.backend.repository;

import com.apex.backend.model.PaperAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaperAccountRepository extends JpaRepository<PaperAccount, Long> {
    Optional<PaperAccount> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
