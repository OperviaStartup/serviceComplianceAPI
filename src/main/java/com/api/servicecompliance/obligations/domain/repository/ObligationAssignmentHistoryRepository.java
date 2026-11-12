package com.api.servicecompliance.obligations.domain.repository;

import com.api.servicecompliance.obligations.domain.model.ObligationAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObligationAssignmentHistoryRepository extends JpaRepository<ObligationAssignmentHistory, Long> {
    List<ObligationAssignmentHistory> findAllByObligationIdOrderByAssignedAtAscIdAsc(Long obligationId);
}
