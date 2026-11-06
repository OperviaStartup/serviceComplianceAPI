package com.api.servicecompliance.executions.infrastructure;

import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Component
@ConditionalOnProperty(name = "app.field-operations.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class ObligationExpiryJob {
    private final ObligationRepository obligations;
    private final Clock clock;
    public ObligationExpiryJob(ObligationRepository obligations, Clock clock) {
        this.obligations=obligations; this.clock=clock;
    }
    @Scheduled(fixedDelayString = "${app.field-operations.expiry-delay-ms:30000}")
    @Transactional
    public void expire() { obligations.expireFieldObligations(clock.instant()); }
}
