package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "execution_step_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"execution_id", "step_id"}))
public class ExecutionStepProgress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long executionId;
    @Column(nullable=false) private Long stepId;
    @Column(nullable=false) private boolean completed;
    @Column(nullable=false) private Instant updatedAt;
    protected ExecutionStepProgress() {}
    public ExecutionStepProgress(Long executionId, Long stepId, Instant now) {
        this.executionId = executionId; this.stepId = stepId; this.updatedAt = now;
    }
    public void update(boolean completed, Instant now) { this.completed = completed; updatedAt = now; }
    public Long getStepId() { return stepId; }
    public boolean isCompleted() { return completed; }
    public Instant getUpdatedAt() { return updatedAt; }
}
