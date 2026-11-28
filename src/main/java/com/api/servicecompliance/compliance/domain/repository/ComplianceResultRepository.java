package com.api.servicecompliance.compliance.domain.repository;

import com.api.servicecompliance.compliance.domain.model.ComplianceResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComplianceResultRepository extends JpaRepository<ComplianceResult, Long> {
    Optional<ComplianceResult> findFirstByComplianceCaseIdOrderByDecisionVersionDesc(Long complianceCaseId);
    boolean existsBySourceObservationId(Long sourceObservationId);
}
