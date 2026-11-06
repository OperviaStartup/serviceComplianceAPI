package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operational_exceptions", uniqueConstraints = @UniqueConstraint(columnNames = {"execution_id", "operation_id"}))
public class OperationalException {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long executionId;
    private Long stepId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ExceptionReason reason;
    @Column(nullable=false,length=2000) private String description;
    @Column(nullable=false) private Long reportedBy;
    @Column(nullable=false) private Instant reportedAt;
    @Column(nullable=false) private UUID operationId;
    protected OperationalException() {}
    public OperationalException(Long executionId, Long stepId, ExceptionReason reason, String description,
                                Long actor, Instant now, UUID operationId) {
        this.executionId=executionId; this.stepId=stepId; this.reason=reason; this.description=description;
        reportedBy=actor; reportedAt=now; this.operationId=operationId;
    }
    public Long getId() { return id; }
    public Long getStepId() { return stepId; }
    public ExceptionReason getReason() { return reason; }
    public String getDescription() { return description; }
    public Long getReportedBy() { return reportedBy; }
    public Instant getReportedAt() { return reportedAt; }
    public UUID getOperationId() { return operationId; }
}
