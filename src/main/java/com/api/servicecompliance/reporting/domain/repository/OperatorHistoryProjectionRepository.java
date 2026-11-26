package com.api.servicecompliance.reporting.domain.repository;

import com.api.servicecompliance.reporting.domain.model.HistoryReviewStatus;
import com.api.servicecompliance.reporting.domain.model.OperatorExecutionHistory;
import java.time.Instant;
import java.util.List;

public interface OperatorHistoryProjectionRepository {
    List<OperatorExecutionHistory> query(HistoryFilter filter, HistoryPosition position, int limit);

    record HistoryFilter(Long companyId, Long siteId, Long operatorId,
                         Instant fromInclusive, Instant toExclusive, String query,
                         HistoryReviewStatus reviewStatus, Instant asOf) {}
    record HistoryPosition(Instant recordedAt, long executionId) {}
}
