package com.api.servicecompliance.obligations.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "obligation_assignment_changes")
public class ObligationAssignmentChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long siteId;

    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false)
    private Long obligationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ObligationAssignmentChangeType changeType;

    @Column(nullable = false)
    private Instant changedAt;

    protected ObligationAssignmentChange() {
    }

    public ObligationAssignmentChange(Long companyId, Long siteId, Long operatorId, Long obligationId,
                                      ObligationAssignmentChangeType changeType, Instant changedAt) {
        this.companyId = companyId;
        this.siteId = siteId;
        this.operatorId = operatorId;
        this.obligationId = obligationId;
        this.changeType = changeType;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getSiteId() { return siteId; }
    public Long getOperatorId() { return operatorId; }
    public Long getObligationId() { return obligationId; }
    public ObligationAssignmentChangeType getChangeType() { return changeType; }
    public Instant getChangedAt() { return changedAt; }
}
