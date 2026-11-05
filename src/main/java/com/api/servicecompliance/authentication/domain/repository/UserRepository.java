package com.api.servicecompliance.authentication.domain.repository;

import com.api.servicecompliance.authentication.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByDocumentNumber(String documentNumber);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDocumentNumber(String documentNumber);
    long countByCompanyIdAndActiveTrue(Long companyId);

    default Optional<User> findByEmail(String email) {
        return findByEmailIgnoreCase(email);
    }

    default boolean existsByEmail(String email) {
        return existsByEmailIgnoreCase(email);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    Optional<User> lockById(@Param("id") Long id);
}
