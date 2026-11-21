package com.api.servicecompliance.executions.domain.repository;

import com.api.servicecompliance.executions.domain.model.OperationalExceptionEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OperationalExceptionEvidenceRepository
        extends JpaRepository<OperationalExceptionEvidence, Long> {
    List<OperationalExceptionEvidence> findByExceptionIdOrderByEvidenceIdAsc(Long exceptionId);
    List<OperationalExceptionEvidence> findByExceptionIdIn(Collection<Long> exceptionIds);
}
