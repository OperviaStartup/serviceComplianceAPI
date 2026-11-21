package com.api.servicecompliance.executions.domain.repository;

import com.api.servicecompliance.executions.domain.model.OfflineSyncOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OfflineSyncOperationRepository extends JpaRepository<OfflineSyncOperation,Long> {
    Optional<OfflineSyncOperation> findByCompanyIdAndActorIdAndClientOperationId(
            Long companyId, Long actorId, UUID clientOperationId);
    long deleteByExpiresAtBefore(Instant now);
}
