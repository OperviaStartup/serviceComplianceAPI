package com.api.servicecompliance.authentication.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RefreshFamilyRepository extends JpaRepository<RefreshFamily, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from RefreshFamily f where f.id = :id")
    Optional<RefreshFamily> lockById(@Param("id") String id);
}
