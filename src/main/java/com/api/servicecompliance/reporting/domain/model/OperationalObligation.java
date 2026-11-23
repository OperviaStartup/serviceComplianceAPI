package com.api.servicecompliance.reporting.domain.model;

import java.time.Instant;

public record OperationalObligation(Long obligationId, Long serviceId, Long siteId, String siteName,
                                    Long operatorId, String operatorName, String activity,
                                    Instant startsAt, Instant dueAt, String timezone,
                                    Long sourcePlanId, Integer sourcePlanVersion,
                                    OperationalStatus status, Instant sortAt) {
}
