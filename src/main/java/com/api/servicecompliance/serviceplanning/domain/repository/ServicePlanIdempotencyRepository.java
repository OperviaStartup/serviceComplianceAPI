package com.api.servicecompliance.serviceplanning.domain.repository;

import com.api.servicecompliance.serviceplanning.domain.model.ServicePlanIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServicePlanIdempotencyRepository extends JpaRepository<ServicePlanIdempotency, Long> {
    Optional<ServicePlanIdempotency> findByCompanyIdAndActorIdAndOperationAndIdempotencyKey(
            Long companyId, Long actorId, String operation, String idempotencyKey);
}
