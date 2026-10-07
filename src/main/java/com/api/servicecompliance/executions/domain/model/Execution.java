package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="executions")
public class Execution {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long obligationId;
    @Column(nullable=false) private Long operatorId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ExecutionResult result;
    @Column(length=2000) private String notes;
    @Column(nullable=false) private Instant executedAt;
    protected Execution(){}
    public Execution(Long obligationId,Long operatorId,ExecutionResult result,String notes){this.obligationId=obligationId;this.operatorId=operatorId;this.result=result;this.notes=notes;this.executedAt=Instant.now();}
    public Long getId(){return id;} public Long getObligationId(){return obligationId;} public Long getOperatorId(){return operatorId;} public ExecutionResult getResult(){return result;} public String getNotes(){return notes;} public Instant getExecutedAt(){return executedAt;}
}
