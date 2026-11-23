package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.model.SiteScope;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.domain.repository.SiteScopeRepository;
import com.api.servicecompliance.authentication.infrastructure.security.JwtService;
import com.api.servicecompliance.executions.domain.model.ExceptionReason;
import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.model.OperationalException;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.executions.domain.repository.OperationalExceptionRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.reporting.application.OperationalStatusQueryService;
import com.api.servicecompliance.reporting.domain.model.OperationalStatus;
import com.api.servicecompliance.serviceplanning.domain.model.ServicePlan;
import com.api.servicecompliance.serviceplanning.domain.model.CompanyService;
import com.api.servicecompliance.serviceplanning.domain.model.ObligationDefinition;
import com.api.servicecompliance.serviceplanning.domain.model.RecurrenceType;
import com.api.servicecompliance.serviceplanning.domain.repository.CompanyServiceRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ObligationDefinitionRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanRepository;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OperationalStatusIntegrationTests.ClockConfiguration.class)
class OperationalStatusIntegrationTests {
    private static final Instant AS_OF = Instant.parse("2030-01-02T15:00:00Z");
    @Autowired OperationalStatusQueryService operationalStatus;
    @Autowired ObligationRepository obligations;
    @Autowired ExecutionRepository executions;
    @Autowired OperationalExceptionRepository exceptions;
    @Autowired ServicePlanRepository plans;
    @Autowired CompanyServiceRepository companyServices;
    @Autowired ObligationDefinitionRepository definitions;
    @Autowired SiteScopeRepository sites;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;

    private User supervisor;
    private User operator;
    private User otherOperator;
    private ServicePlan plan;
    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        clock.set(AS_OF);
        supervisor = user("Supervisor", Role.SUPERVISOR, 1L);
        operator = user("Operator", Role.OPERATOR, 1L);
        otherOperator = user("Other operator", Role.OPERATOR, 1L);
        companyService = companyServices.saveAndFlush(new CompanyService(1L, "OPS-" + UUID.randomUUID(), "Operations"));
        plan = plans.saveAndFlush(new ServicePlan(1L, companyService.getId(), 1L, LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 31), supervisor.getId(), AS_OF.minusSeconds(86_400)));
    }

    @AfterEach
    void cleanUp() {
        exceptions.deleteAll();
        executions.deleteAll();
        obligations.deleteAll();
        definitions.deleteAll();
        plans.deleteAll();
        if (companyService != null && companyService.getId() != null)
            companyServices.findById(companyService.getId()).ifPresent(companyServices::delete);
        users.deleteAll();
        sites.findById(2L).ifPresent(sites::delete);
    }

    @Test
    void derivesCanonicalPrecedenceAndReconcilesAuthorizedCounts() {
        save(operator, AS_OF.minusSeconds(14_400), AS_OF.plusSeconds(3_600), State.ASSIGNED);
        save(operator, AS_OF.minusSeconds(10_800), AS_OF.plusSeconds(3_600), State.IN_PROGRESS);
        save(operator, AS_OF.minusSeconds(7_200), AS_OF.plusSeconds(3_600), State.SUBMITTED);
        save(operator, AS_OF.minusSeconds(3_600), AS_OF, State.OVERDUE);
        save(operator, AS_OF.minusSeconds(1_800), AS_OF.plusSeconds(3_600), State.EXCEPTED_SUBMITTED);
        if (!sites.existsById(2L)) sites.saveAndFlush(new SiteScope(2L, 1L, "Other site"));
        User outside = user("Outside", Role.OPERATOR, 2L);
        save(outside, AS_OF.minusSeconds(900), AS_OF.plusSeconds(3_600), State.ASSIGNED);

        var page = operationalStatus.query(companyService.getId(), 1L, LocalDate.of(2030, 1, 2),
                LocalDate.of(2030, 1, 2), null, null, null, 20, supervisor);

        assertThat(page.items()).extracting(item -> item.status()).containsExactly(
                OperationalStatus.ASSIGNED, OperationalStatus.IN_PROGRESS, OperationalStatus.SUBMITTED,
                OperationalStatus.OVERDUE, OperationalStatus.EXCEPTED);
        assertThat(page.total()).isEqualTo(5);
        assertThat(page.counts()).containsEntry(OperationalStatus.ASSIGNED, 1L)
                .containsEntry(OperationalStatus.IN_PROGRESS, 1L)
                .containsEntry(OperationalStatus.SUBMITTED, 1L)
                .containsEntry(OperationalStatus.OVERDUE, 1L)
                .containsEntry(OperationalStatus.EXCEPTED, 1L);
        assertThat(page.items()).allSatisfy(item -> assertThat(item.siteId()).isEqualTo(1L));
    }

    @Test
    void keepsFilteredKeysetPagesStableAndRejectsCursorReuseWithOtherFilters() {
        Obligation first = save(operator, AS_OF.minusSeconds(7_200), AS_OF.plusSeconds(3_600), State.ASSIGNED);
        Obligation second = save(operator, AS_OF.minusSeconds(3_600), AS_OF.plusSeconds(3_600), State.ASSIGNED);
        Obligation third = save(otherOperator, AS_OF.minusSeconds(1_800), AS_OF.plusSeconds(3_600), State.ASSIGNED);
        Obligation legacy = Obligation.configured("Legacy", "Legacy", "Site", AS_OF.minusSeconds(900),
                AS_OF.plusSeconds(3_600), 1L, 1L, List.of(), List.of());
        legacy.assignTo(operator.getId());
        obligations.saveAndFlush(legacy);

        var firstPage = operationalStatus.query(companyService.getId(), null, null, null, null,
                Set.of(OperationalStatus.ASSIGNED), null, 2, supervisor);
        assertThat(firstPage.items()).extracting(item -> item.obligationId()).containsExactly(first.getId(), second.getId());
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.hasMore()).isTrue();

        var secondPage = operationalStatus.query(companyService.getId(), null, null, null, null,
                Set.of(OperationalStatus.ASSIGNED), firstPage.nextCursor(), 2, supervisor);
        assertThat(secondPage.items()).extracting(item -> item.obligationId()).containsExactly(third.getId());
        assertThat(secondPage.total()).isEqualTo(3);
        assertThat(secondPage.asOf()).isEqualTo(firstPage.asOf());

        assertThatThrownBy(() -> operationalStatus.query(companyService.getId(), null, null, null, operator.getId(),
                Set.of(OperationalStatus.ASSIGNED), firstPage.nextCursor(), 2, supervisor))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void enforcesSupervisorRoleAndSiteAtTheHttpBoundary() throws Exception {
        save(operator, AS_OF.minusSeconds(3_600), AS_OF.plusSeconds(3_600), State.ASSIGNED);
        String supervisorToken = "Bearer " + jwt.createToken(supervisor);
        String operatorToken = "Bearer " + jwt.createToken(operator);

        mvc.perform(get("/api/v1/operational-obligations")
                        .header("Authorization", supervisorToken)
                        .param("serviceId", companyService.getId().toString())
                        .param("status", "ASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.counts.ASSIGNED").value(1));
        mvc.perform(get("/api/v1/operational-obligations")
                        .header("Authorization", operatorToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operational-obligations")
                        .header("Authorization", supervisorToken).param("siteId", "2"))
                .andExpect(status().isForbidden());
    }

    private Obligation save(User assignee, Instant startsAt, Instant dueAt, State state) {
        ObligationDefinition definition = definitions.saveAndFlush(new ObligationDefinition(plan.getId(), 1L,
                "Operational work", "Work", "Complete", RecurrenceType.DAILY, null, 1, Set.of(),
                LocalTime.ofInstant(startsAt, ZoneId.of("America/Lima")), 60, List.of(),
                supervisor.getId(), AS_OF.minusSeconds(86_000)));
        Obligation obligation = Obligation.generated("Operational work", "Work", "Site", startsAt, dueAt,
                1L, assignee.getSiteId(), plan.getId(), 1, definition.getId(), definition.getVersion(),
                LocalDate.ofInstant(startsAt, ZoneId.of("America/Lima")), List.of(), List.of());
        obligation.assignTo(assignee.getId());
        if (state == State.IN_PROGRESS) obligation.markInProgress();
        if (state == State.SUBMITTED || state == State.EXCEPTED_SUBMITTED) obligation.markSubmitted();
        obligation = obligations.saveAndFlush(obligation);
        if (state == State.EXCEPTED_SUBMITTED) {
            Execution execution = Execution.start(obligation.getId(), assignee.getId(), startsAt.plusSeconds(60));
            execution.submit("Completed with exception", startsAt.plusSeconds(120));
            execution = executions.saveAndFlush(execution);
            exceptions.saveAndFlush(new OperationalException(execution.getId(), null, ExceptionReason.OTHER,
                    "Reported issue", assignee.getId(), startsAt.plusSeconds(90), UUID.randomUUID()));
        }
        return obligation;
    }

    private User user(String name, Role role, Long siteId) {
        return users.saveAndFlush(new User(name, UUID.randomUUID() + "@example.test",
                passwords.encode("Password123!"), role, 1L, siteId));
    }

    private enum State { ASSIGNED, IN_PROGRESS, SUBMITTED, OVERDUE, EXCEPTED_SUBMITTED }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(AS_OF);
        void set(Instant value) { now.set(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
