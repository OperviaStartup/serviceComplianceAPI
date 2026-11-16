package com.api.servicecompliance.executions.domain.repository;

import com.api.servicecompliance.executions.domain.model.EvidenceIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface EvidenceIdempotencyRepository extends JpaRepository<EvidenceIdempotency, Long> {
    Optional<EvidenceIdempotency> findByCompanyIdAndActorIdAndCommandAndIdempotencyKey(
            Long companyId, Long actorId, String command, String idempotencyKey);
    long deleteByExpiresAtBefore(Instant now);
}
