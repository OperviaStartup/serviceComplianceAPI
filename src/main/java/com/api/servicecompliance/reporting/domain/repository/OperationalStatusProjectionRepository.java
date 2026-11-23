package com.api.servicecompliance.reporting.domain.repository;

import com.api.servicecompliance.reporting.domain.model.OperationalObligation;
import com.api.servicecompliance.reporting.domain.model.OperationalStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface OperationalStatusProjectionRepository {
    ProjectionResult query(ProjectionFilter filter, ProjectionPosition position, int limit);

    record ProjectionFilter(Long companyId, Long siteId, Long serviceId, Long operatorId,
                            Instant fromInclusive, Instant toExclusive, Set<OperationalStatus> statuses,
                            Instant asOf) {
    }

    record ProjectionPosition(Instant sortAt, Long obligationId) {
    }

    record ProjectionResult(List<OperationalObligation> items, Map<OperationalStatus, Long> counts) {
    }
}
