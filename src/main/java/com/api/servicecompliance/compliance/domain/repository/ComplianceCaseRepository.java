package com.api.servicecompliance.compliance.domain.repository;

import com.api.servicecompliance.compliance.domain.model.ComplianceCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComplianceCaseRepository extends JpaRepository<ComplianceCase, Long> {
    Optional<ComplianceCase> findByObligationId(Long obligationId);
}
