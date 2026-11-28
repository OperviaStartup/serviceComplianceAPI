package com.api.servicecompliance.compliance.domain.repository;

import com.api.servicecompliance.compliance.domain.model.CorrectiveActionSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectiveActionSubmissionRepository
        extends JpaRepository<CorrectiveActionSubmission, Long> {
    long countByCorrectiveActionId(Long correctiveActionId);
    List<CorrectiveActionSubmission> findAllByCorrectiveActionIdOrderBySubmissionNumberAsc(
            Long correctiveActionId);
}
