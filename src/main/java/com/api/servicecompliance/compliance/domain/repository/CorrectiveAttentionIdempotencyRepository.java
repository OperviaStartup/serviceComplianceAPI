package com.api.servicecompliance.compliance.domain.repository;

import com.api.servicecompliance.compliance.domain.model.CorrectiveAttentionIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface CorrectiveAttentionIdempotencyRepository
        extends JpaRepository<CorrectiveAttentionIdempotency, Long> {
    Optional<CorrectiveAttentionIdempotency> findByCompanyIdAndActorIdAndCommandAndIdempotencyKey(
            Long companyId, Long actorId, String command, String idempotencyKey);
    long deleteByExpiresAtBefore(Instant now);
}
