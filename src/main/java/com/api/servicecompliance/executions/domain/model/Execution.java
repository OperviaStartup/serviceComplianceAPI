package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="executions")
public class Execution {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long obligationId;
    @Column(nullable=false) private Long operatorId;
    @Enumerated(EnumType.STRING) private ExecutionResult result;
    @Column(length=2000) private String notes;
    @Column(nullable=false) private Instant executedAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ExecutionState state = ExecutionState.LEGACY;
    private Instant startedAt;
    private Instant submittedAt;
    private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    @Column(nullable=false) private int completedSteps;
    @Column(nullable=false) private int attachedPhotos;
    @Column(nullable=false) private int exceptionCount;
    protected Execution(){}
    public Execution(Long obligationId,Long operatorId,ExecutionResult result,String notes){this.obligationId=obligationId;this.operatorId=operatorId;this.result=result;this.notes=notes;this.executedAt=Instant.now();}
    public static Execution start(Long obligationId, Long operatorId, Instant now) {
        Execution execution = new Execution();
        execution.obligationId = obligationId;
        execution.operatorId = operatorId;
        execution.executedAt = now;
        execution.startedAt = now;
        execution.updatedAt = now;
        execution.state = ExecutionState.IN_PROGRESS;
        return execution;
    }
    public void stepChanged(boolean completed, Instant now) {
        completedSteps += completed ? 1 : -1;
        updatedAt = now;
    }
    public void photoAttached(Instant now) { attachedPhotos++; updatedAt = now; }
    public void exceptionReported(Instant now) { exceptionCount++; updatedAt = now; }
    public void submit(String notes, Instant now) {
        state = ExecutionState.SUBMITTED;
        result = ExecutionResult.COMPLETED;
        this.notes = notes;
        submittedAt = now;
        updatedAt = now;
    }
    public ExecutionState getState() { return state; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public Long getId(){return id;} public Long getObligationId(){return obligationId;} public Long getOperatorId(){return operatorId;} public ExecutionResult getResult(){return result;} public String getNotes(){return notes;} public Instant getExecutedAt(){return executedAt;}
}
