package com.api.servicecompliance.authentication.domain.repository;

import com.api.servicecompliance.authentication.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> { Optional<User> findByEmail(String email); boolean existsByEmail(String email); @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from User u where u.id = :id") Optional<User> lockById(@Param("id") Long id); }
