package com.api.servicecompliance.compliance.domain.repository;

import com.api.servicecompliance.compliance.domain.model.CorrectiveActionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorrectiveActionEvidenceRepository extends JpaRepository<CorrectiveActionEvidence, Long> {
    Optional<CorrectiveActionEvidence> findByIdAndCorrectiveActionId(Long id, Long correctiveActionId);
}
