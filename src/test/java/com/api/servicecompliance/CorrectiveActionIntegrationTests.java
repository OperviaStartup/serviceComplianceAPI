package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.infrastructure.security.JwtService;
import com.api.servicecompliance.compliance.application.CorrectiveActionService;
import com.api.servicecompliance.compliance.domain.model.ComplianceCase;
import com.api.servicecompliance.compliance.domain.model.ComplianceOutcome;
import com.api.servicecompliance.compliance.domain.model.ComplianceResult;
import com.api.servicecompliance.compliance.domain.model.CorrectiveActionState;
import com.api.servicecompliance.compliance.domain.model.CorrectiveActionStatus;
import com.api.servicecompliance.compliance.domain.repository.ComplianceCaseRepository;
import com.api.servicecompliance.compliance.domain.repository.ComplianceResultRepository;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveActionRepository;
import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.model.ExecutionResult;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CorrectiveActionIntegrationTests.ClockConfiguration.class)
class CorrectiveActionIntegrationTests {
    private static final Instant NOW = Instant.parse("2031-03-04T15:00:00Z");

    @Autowired CorrectiveActionService service;
    @Autowired CorrectiveActionRepository actions;
    @Autowired ComplianceResultRepository results;
    @Autowired ComplianceCaseRepository cases;
    @Autowired ExecutionRepository executions;
    @Autowired ObligationRepository obligations;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private User supervisor;
    private User operator;
    private ComplianceCase complianceCase;
    private ComplianceResult originalResult;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        clock.set(NOW);
        supervisor = user("Supervisor", Role.SUPERVISOR);
        operator = user("Operator", Role.OPERATOR);
        Obligation obligation = obligations.saveAndFlush(new Obligation(
                "Correct fixture", "Correct fixture", "Site 1", operator.getId(),
                NOW.plusSeconds(3600), 1L, 1L));
        Execution execution = executions.saveAndFlush(new Execution(
                obligation.getId(), operator.getId(), ExecutionResult.COMPLETED, "Submitted facts"));
        complianceCase = new ComplianceCase(obligation.getId(), 1L, 1L, supervisor.getId(), NOW);
        long decisionVersion = complianceCase.appendDecision(NOW);
        complianceCase = cases.saveAndFlush(complianceCase);
        originalResult = results.saveAndFlush(new ComplianceResult(
                complianceCase.getId(), execution.getId(), decisionVersion, null,
                ComplianceOutcome.NON_COMPLIANT, "execution-v0", 0,
                null, null, null, null, "Evidence did not meet the criterion", null,
                supervisor.getId(), NOW, List.of(), List.of()));
    }

    @AfterEach
    void cleanUp() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        actions.deleteAll();
        actions.flush();
        results.findAll().stream()
                .sorted(java.util.Comparator.comparingLong(ComplianceResult::getDecisionVersion).reversed())
                .forEach(results::delete);
        results.flush();
        cases.deleteAll();
        executions.deleteAll();
        obligations.deleteAll();
        users.deleteAll();
    }

    @Test
    void supervisorCreatesAndAssignedOperatorReadsWithoutChangingDecision() throws Exception {
        var command = new CorrectiveActionService.CreateCorrectiveActionCommand(
                operator.getId(), "  Replace the damaged seal  ", LocalDate.of(2031, 3, 5));

        String response = mvc.perform(post("/api/v1/compliance-cases/{id}/corrective-actions",
                        complianceCase.getId())
                        .header("Authorization", "Bearer " + jwt.createToken(supervisor))
                        .contentType("application/json")
                        .content(json.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("ASSIGNED"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.timezone").value("America/Lima"))
                .andExpect(jsonPath("$.description").value("Replace the damaged seal"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.events[0].actionVersion").value(0))
                .andReturn().getResponse().getContentAsString();

        long actionId = json.readTree(response).get("id").asLong();
        mvc.perform(get("/api/v1/corrective-actions/{id}", actionId)
                        .header("Authorization", "Bearer " + jwt.createToken(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(operator.getId()));

        assertThat(results.findById(originalResult.getId()).orElseThrow().getOutcome())
                .isEqualTo(ComplianceOutcome.NON_COMPLIANT);
        assertThat(results.count()).isOne();
    }

    @Test
    void enforcesLatestResultEligibilityAssigneeAndOperatorOwnership() {
        User otherOperator = user("Other operator", Role.OPERATOR);
        var first = service.create(complianceCase.getId(), command(operator), supervisor);
        var second = service.create(complianceCase.getId(), command(operator), supervisor);
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(actions.count()).isEqualTo(2);
        assertThatThrownBy(() -> service.get(first.id(), otherOperator))
                .isInstanceOf(AccessDeniedException.class);

        otherOperator.suspend();
        users.saveAndFlush(otherOperator);
        assertThatThrownBy(() -> service.create(complianceCase.getId(), command(otherOperator), supervisor))
                .isInstanceOf(AccessDeniedException.class);

        complianceCase.appendDecision(NOW.plusSeconds(60));
        cases.saveAndFlush(complianceCase);
        results.saveAndFlush(new ComplianceResult(
                complianceCase.getId(), originalResult.getExecutionId(), 2, originalResult.getId(),
                ComplianceOutcome.COMPLIANT, "execution-v0", 0,
                null, null, null, null, "Reevaluation passed", "Evidence corrected",
                supervisor.getId(), NOW.plusSeconds(60), List.of(), List.of()));

        assertThatThrownBy(() -> service.create(complianceCase.getId(), command(operator), supervisor))
                .isInstanceOf(ApiConflictException.class)
                .extracting(error -> ((ApiConflictException) error).getCode())
                .isEqualTo("CORRECTIVE_ACTION_NOT_ELIGIBLE");

    }

    @Test
    void followsApprovedLifecycleAndPreservesImmutableHistory() {
        var created = service.create(complianceCase.getId(), command(operator), supervisor);
        var submitted = service.submitAttention(created.id(), transition(0, "Work completed"), operator);
        var returned = service.returnForCorrection(created.id(), transition(1, "Photo is unclear"), supervisor);
        var resubmitted = service.submitAttention(created.id(), transition(2, "New photo attached"), operator);

        assertThatThrownBy(() -> service.verifyAndClose(created.id(), transition(2, "Stale close"), supervisor))
                .isInstanceOf(ApiConflictException.class)
                .extracting(error -> ((ApiConflictException) error).getCode())
                .isEqualTo("VERSION_CONFLICT");
        assertThatThrownBy(() -> service.verifyAndClose(created.id(), transition(3, "Self close"), operator))
                .isInstanceOf(AccessDeniedException.class);

        var closed = service.verifyAndClose(created.id(), transition(3, "Verified on site"), supervisor);
        assertThat(submitted.state()).isEqualTo(CorrectiveActionState.ATTENTION_SUBMITTED);
        assertThat(returned.state()).isEqualTo(CorrectiveActionState.RETURNED);
        assertThat(resubmitted.version()).isEqualTo(3);
        assertThat(closed.state()).isEqualTo(CorrectiveActionState.CLOSED);
        assertThat(closed.closedBy()).isEqualTo(supervisor.getId());
        assertThat(closed.events()).extracting(CorrectiveActionService.CorrectiveActionEventResponse::actionVersion)
                .containsExactly(0L, 1L, 2L, 3L, 4L);
        assertThatThrownBy(() -> service.returnForCorrection(created.id(), transition(4, "Reopen"), supervisor))
                .isInstanceOf(ApiConflictException.class)
                .extracting(error -> ((ApiConflictException) error).getCode())
                .isEqualTo("CORRECTIVE_ACTION_INVALID_STATE");
        assertThat(results.count()).isOne();
    }

    @Test
    void derivesOverdueAtMidnightAfterInclusiveTargetDateInLima() {
        var created = service.create(complianceCase.getId(), new CorrectiveActionService.CreateCorrectiveActionCommand(
                operator.getId(), "Due boundary", LocalDate.of(2031, 3, 4)), supervisor);
        clock.set(Instant.parse("2031-03-05T04:59:59Z"));
        assertThat(service.get(created.id(), operator).status()).isEqualTo(CorrectiveActionStatus.ASSIGNED);
        clock.set(Instant.parse("2031-03-05T05:00:00Z"));
        assertThat(service.get(created.id(), operator).status()).isEqualTo(CorrectiveActionStatus.OVERDUE);
        var submitted = service.submitAttention(created.id(), transition(0, "Submitted late"), operator);
        assertThat(submitted.status()).isEqualTo(CorrectiveActionStatus.ATTENTION_SUBMITTED);
    }

    @Test
    void serializesConcurrentCommandsWithTheSameExpectedVersion() throws Exception {
        var created = service.create(complianceCase.getId(), command(operator), supervisor);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentSubmit(created.id(), ready, start, "First submit"));
            var second = executor.submit(() -> concurrentSubmit(created.id(), ready, start, "Second submit"));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("ATTENTION_SUBMITTED", "VERSION_CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        var stored = service.get(created.id(), operator);
        assertThat(stored.version()).isEqualTo(1);
        assertThat(stored.events()).hasSize(2);
    }

    private String concurrentSubmit(long actionId, CountDownLatch ready,
                                    CountDownLatch start, String reason) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return service.submitAttention(actionId, transition(0, reason), operator).state().name();
        } catch (ApiConflictException conflict) {
            return conflict.getCode();
        }
    }

    private CorrectiveActionService.CreateCorrectiveActionCommand command(User assignee) {
        return new CorrectiveActionService.CreateCorrectiveActionCommand(
                assignee.getId(), "Corrective work", LocalDate.of(2031, 3, 5));
    }

    private CorrectiveActionService.TransitionCommand transition(long version, String reason) {
        return new CorrectiveActionService.TransitionCommand(version, reason);
    }

    private User user(String name, Role role) {
        return users.saveAndFlush(new User(name, name.toLowerCase().replace(' ', '-') + "-"
                + UUID.randomUUID() + "@example.test", passwords.encode("Password123!"), role, 1L, 1L));
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private Instant instant = NOW;
        void set(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
