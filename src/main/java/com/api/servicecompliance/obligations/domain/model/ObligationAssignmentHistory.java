package com.api.servicecompliance.obligations.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "obligation_assignment_history")
public class ObligationAssignmentHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long obligationId;
    private Long previousOperatorId;
    @Column(nullable = false) private Long newOperatorId;
    @Column(nullable = false) private Long actorId;
    @Column(nullable = false) private Instant assignedAt;
    @Column(nullable = false, length = 2000) private String reason;
    protected ObligationAssignmentHistory() {}
    public ObligationAssignmentHistory(Long obligationId, Long previousOperatorId, Long newOperatorId,
                                       Long actorId, Instant assignedAt, String reason) {
        this.obligationId = obligationId;
        this.previousOperatorId = previousOperatorId;
        this.newOperatorId = newOperatorId;
        this.actorId = actorId;
        this.assignedAt = assignedAt;
        this.reason = reason;
    }
}
