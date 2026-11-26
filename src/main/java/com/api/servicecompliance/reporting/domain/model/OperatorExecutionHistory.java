package com.api.servicecompliance.reporting.domain.model;

import com.api.servicecompliance.compliance.domain.model.ComplianceOutcome;
import com.api.servicecompliance.executions.domain.model.ExecutionResult;
import com.api.servicecompliance.executions.domain.model.ExecutionState;
import com.api.servicecompliance.executions.domain.model.SyncOutcome;
import java.time.Instant;

public record OperatorExecutionHistory(
        Long id, Long obligationId, String activity, String siteName, String timezone,
        ExecutionState state, ExecutionResult result, Instant executedAt, Instant submittedAt, String notes,
        SyncOutcome synchronizationState, ComplianceResultView complianceResult) {
    public Instant recordedAt() { return submittedAt == null ? executedAt : submittedAt; }

    public record ComplianceResultView(Long id, Long caseId, ComplianceOutcome outcome,
                                       long decisionVersion, Instant decidedAt, String explanation) {}
}
