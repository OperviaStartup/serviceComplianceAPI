package com.api.servicecompliance.executions.domain.repository;

import com.api.servicecompliance.executions.domain.model.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Evidence,Long> {
    List<Evidence> findByExecutionId(Long executionId);
    Optional<Evidence> findByExecutionIdAndPhotoRequirementId(Long executionId, Long requirementId);
}
