package com.api.servicecompliance.executions.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@EnableScheduling
public class FieldOperationsConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock fieldOperationsClock() { return Clock.systemUTC(); }
}
