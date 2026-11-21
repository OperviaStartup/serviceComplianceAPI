package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "operational_exception_evidence", uniqueConstraints = @UniqueConstraint(
        name = "uq_operational_exception_evidence",
        columnNames = {"exception_id", "evidence_id"}))
public class OperationalExceptionEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long exceptionId;

    @Column(nullable = false)
    private Long evidenceId;

    @Column(nullable = false)
    private Long executionId;

    protected OperationalExceptionEvidence() {
    }

    public OperationalExceptionEvidence(Long exceptionId, Long evidenceId, Long executionId) {
        this.exceptionId = exceptionId;
        this.evidenceId = evidenceId;
        this.executionId = executionId;
    }

    public Long getExceptionId() { return exceptionId; }
    public Long getEvidenceId() { return evidenceId; }
    public Long getExecutionId() { return executionId; }
}
