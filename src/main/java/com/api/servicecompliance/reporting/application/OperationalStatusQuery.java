package com.api.servicecompliance.reporting.application;

import com.api.servicecompliance.reporting.domain.OperationalStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface OperationalStatusQuery {
    Result read(Filters filters, Position after, Instant asOf, int fetchSize);

    record Filters(long companyId, long siteId, Long serviceId, Long operatorId,
                   Instant fromInclusive, Instant toExclusive, OperationalStatus status) {}

    record Position(Instant sortAt, long id) {}

    record Item(long id, Long serviceId, long siteId, String siteName, long operatorId, String activity,
                Instant startsAt, Instant dueAt, String timezone, Long sourcePlanId,
                Integer sourcePlanVersion, OperationalStatus status) {}

    record Result(List<Item> items, Map<OperationalStatus, Long> counts) {}
}
