package com.api.servicecompliance.executions.domain.repository;

import com.api.servicecompliance.executions.domain.model.ExecutionIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface ExecutionIdempotencyRepository extends JpaRepository<ExecutionIdempotency, Long> {
    Optional<ExecutionIdempotency> findByCompanyIdAndActorIdAndCommandAndIdempotencyKey(
            Long companyId, Long actorId, String command, String idempotencyKey);

    boolean existsByExecutionIdAndCommand(Long executionId, String command);

    long deleteByExpiresAtBefore(Instant now);
}
