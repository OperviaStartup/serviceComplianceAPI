package com.api.servicecompliance.executions.domain.repository;

import com.api.servicecompliance.executions.domain.model.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence,Long> { List<Evidence> findByExecutionId(Long executionId); }
