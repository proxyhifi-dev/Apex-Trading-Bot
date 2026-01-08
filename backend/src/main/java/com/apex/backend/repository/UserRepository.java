package com.apex.backend.repository;

import com.apex.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository for User entity operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by username
     * @param username the username
     * @return Optional containing user if found
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find user by email
     * @param email the email
     * @return Optional containing user if found
     */
    Optional<User> findByEmail(String email);

    Optional<User> findByFyersId(String fyersId);
    
    /**
     * Check if username exists
     * @param username the username
     * @return true if exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email exists
     * @param email the email
     * @return true if exists
     */
    boolean existsByEmail(String email);
}
