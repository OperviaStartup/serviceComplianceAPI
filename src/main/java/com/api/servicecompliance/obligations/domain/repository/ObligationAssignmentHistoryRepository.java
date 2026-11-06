package com.api.servicecompliance.obligations.domain.repository;

import com.api.servicecompliance.obligations.domain.model.ObligationAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObligationAssignmentHistoryRepository extends JpaRepository<ObligationAssignmentHistory, Long> {}
