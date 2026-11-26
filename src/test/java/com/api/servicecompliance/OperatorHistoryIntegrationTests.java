package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.model.SiteScope;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.domain.repository.SiteScopeRepository;
import com.api.servicecompliance.authentication.infrastructure.security.JwtService;
import com.api.servicecompliance.compliance.domain.model.*;
import com.api.servicecompliance.compliance.domain.repository.*;
import com.api.servicecompliance.executions.domain.model.*;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.reporting.application.OperatorHistoryQueryService;
import com.api.servicecompliance.reporting.domain.model.HistoryReviewStatus;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.Role;
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
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(OperatorHistoryIntegrationTests.ClockConfiguration.class)
class OperatorHistoryIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-03T15:00:00Z");
    @Autowired OperatorHistoryQueryService history;
    @Autowired ObligationRepository obligations;
    @Autowired ExecutionRepository executions;
    @Autowired ComplianceCaseRepository cases;
    @Autowired ComplianceResultRepository results;
    @Autowired UserRepository users;
    @Autowired SiteScopeRepository sites;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    private User operator;
    private User other;
    private User supervisor;

    @BeforeEach void setUp() {
        clock.set(NOW);
        operator = user(Role.OPERATOR, 1L, 1L);
        other = user(Role.OPERATOR, 1L, 1L);
        supervisor = user(Role.SUPERVISOR, 1L, 1L);
    }

    @Test void returnsOnlyOwnedSubmittedHistoryAndKeepsAllThreeStatesSeparate() {
        Execution pending = submitted(operator, "Pending review", NOW.minusSeconds(3600));
        Execution compliant = submitted(operator, "Evaluated", NOW.minusSeconds(1800));
        decide(compliant, ComplianceOutcome.COMPLIANT, NOW.minusSeconds(900));
        submitted(other, "Private other operator", NOW.minusSeconds(100));
        Obligation active = obligation(operator, "In progress", 1L, 1L);
        executions.saveAndFlush(Execution.start(active.getId(), operator.getId(), NOW.minusSeconds(60)));
        var page = query(null, null, null, null, null, 50, operator);
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(compliant.getId(), pending.getId());
        assertThat(page.items()).allSatisfy(item -> {
            assertThat(item.state()).isEqualTo(ExecutionState.SUBMITTED);
            assertThat(item.result()).isEqualTo(ExecutionResult.COMPLETED);
            assertThat(item.synchronizationState()).isEqualTo(SyncOutcome.SYNCED);
        });
        assertThat(page.items().get(0).complianceResult().outcome()).isEqualTo(ComplianceOutcome.COMPLIANT);
        assertThat(page.items().get(1).complianceResult()).isNull();
    }

    @Test void filtersOnlyExplicitComplianceDecisionsAndUsesLatestVersion() {
        Execution pending = submitted(operator, "Pending", NOW.minusSeconds(4000));
        Execution evaluated = submitted(operator, "Evaluated", NOW.minusSeconds(3000));
        decide(evaluated, ComplianceOutcome.COMPLIANT, NOW.minusSeconds(2500));
        decide(evaluated, ComplianceOutcome.NON_COMPLIANT, NOW.minusSeconds(2000));
        Execution excepted = submitted(operator, "Excepted", NOW.minusSeconds(1000));
        decide(excepted, ComplianceOutcome.EXCEPTION_ACCEPTED, NOW.minusSeconds(500));
        assertThat(query(null, null, null, HistoryReviewStatus.PENDING_REVIEW, null, 50, operator).items())
                .extracting(item -> item.id()).containsExactly(pending.getId());
        assertThat(query(null, null, null, HistoryReviewStatus.COMPLIANT, null, 50, operator).items()).isEmpty();
        var nonCompliant = query(null, null, null, HistoryReviewStatus.NON_COMPLIANT, null, 50, operator).items();
        assertThat(nonCompliant).hasSize(1);
        assertThat(nonCompliant.get(0).id()).isEqualTo(evaluated.getId());
        assertThat(nonCompliant.get(0).complianceResult().decisionVersion()).isEqualTo(2);
        assertThat(query(null, null, null, HistoryReviewStatus.EXCEPTION_ACCEPTED, null, 50, operator).items())
                .extracting(item -> item.id()).containsExactly(excepted.getId());
    }

    @Test void inclusiveLimaDatesUseSubmissionTimeRatherThanStartTime() {
        submitted(operator, "Before", Instant.parse("2030-01-02T04:59:59Z"));
        Execution first = submitted(operator, "At Lima start", Instant.parse("2030-01-02T05:00:00Z"));
        Execution last = submitted(operator, "At Lima end", Instant.parse("2030-01-03T04:59:59Z"));
        submitted(operator, "Next day", Instant.parse("2030-01-03T05:00:00Z"));
        var page = query(LocalDate.of(2030, 1, 2), LocalDate.of(2030, 1, 2), null, null, null, 50, operator);
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(last.getId(), first.getId());
    }

    @Test void titleSearchTreatsWildcardsAsLiteralCharacters() {
        Execution expected = submitted(operator, "Recepción 100%_ lista", NOW.minusSeconds(30));
        submitted(operator, "Recepción 100XX lista", NOW.minusSeconds(20));
        assertThat(query(null, null, "%_", null, null, 50, operator).items())
                .extracting(item -> item.id()).containsExactly(expected.getId());
        assertThat(query(null, null, "recepción", null, null, 50, operator).items()).hasSize(2);
    }

    @Test void equalTimestampPagesHaveNoDuplicatesAndExcludeNewerWork() {
        Instant same = NOW.minusSeconds(3600);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) ids.add(submitted(operator, "Page " + i, same).getId());
        var first = query(null, null, null, null, null, 2, operator);
        assertThat(first.hasMore()).isTrue();
        clock.set(NOW.plusSeconds(30));
        submitted(operator, "Created after first page", NOW.plusSeconds(10));
        var second = query(null, null, null, null, first.nextCursor(), 2, operator);
        List<Long> actual = new ArrayList<>();
        first.items().forEach(item -> actual.add(item.id()));
        second.items().forEach(item -> actual.add(item.id()));
        ids.sort(Comparator.reverseOrder());
        assertThat(actual).containsExactlyElementsOf(ids);
        assertThat(second.asOf()).isEqualTo(first.asOf());
        assertThat(second.hasMore()).isFalse();
    }

    @Test void newDecisionsDoNotChangeAnExistingPageCutoff() {
        Execution older = submitted(operator, "Older", NOW.minusSeconds(1000));
        submitted(operator, "Newer", NOW.minusSeconds(500));
        var first = query(null, null, null, HistoryReviewStatus.PENDING_REVIEW, null, 1, operator);
        clock.set(NOW.plusSeconds(30));
        decide(older, ComplianceOutcome.COMPLIANT, NOW.plusSeconds(10));
        var next = query(null, null, null, HistoryReviewStatus.PENDING_REVIEW, first.nextCursor(), 1, operator);
        assertThat(next.items()).extracting(item -> item.id()).containsExactly(older.getId());
        assertThat(next.items().get(0).complianceResult()).isNull();
        assertThat(query(null, null, null, HistoryReviewStatus.COMPLIANT, null, 50, operator).items())
                .extracting(item -> item.id()).containsExactly(older.getId());
    }

    @Test void cursorCannotBeReusedForAnotherAccountOrFilter() {
        submitted(operator, "One", NOW.minusSeconds(100));
        submitted(operator, "Two", NOW.minusSeconds(50));
        String cursor = query(null, null, null, null, null, 1, operator).nextCursor();
        assertThatThrownBy(() -> query(null, null, "different", null, cursor, 1, operator)).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> query(null, null, null, null, cursor, 1, other)).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> query(null, null, null, null, "not-base64!", 1, operator)).isInstanceOf(FieldValidationException.class);
        clock.set(NOW.minusSeconds(1));
        assertThatThrownBy(() -> query(null, null, null, null, cursor, 1, operator)).isInstanceOf(FieldValidationException.class);
    }

    @Test void ownershipDoesNotFollowCurrentAssignmentOrExposeOtherSites() {
        Execution own = submitted(operator, "Historical ownership", NOW.minusSeconds(100));
        Obligation reassigned = obligations.findById(own.getObligationId()).orElseThrow();
        reassigned.assignTo(other.getId()); obligations.saveAndFlush(reassigned);
        if (!sites.existsById(207L)) sites.saveAndFlush(new SiteScope(207L, 1L, "Other site"));
        Obligation outside = obligation(operator, "Other site", 1L, 207L);
        Execution outsideExecution = Execution.start(outside.getId(), operator.getId(), NOW.minusSeconds(90));
        outsideExecution.submit("Outside", NOW.minusSeconds(80)); executions.saveAndFlush(outsideExecution);
        assertThat(query(null, null, null, null, null, 50, operator).items()).extracting(item -> item.id()).containsExactly(own.getId());
        assertThat(query(null, null, null, null, null, 50, other).items()).isEmpty();
        User foreignCompany = user(Role.OPERATOR, 2L, 207L);
        assertThat(query(null, null, null, null, null, 50, foreignCompany).items()).isEmpty();
    }

    @Test void legacyHistoryKeepsItsDateAndNeverCreatesAutomaticCompliance() {
        Obligation obligation = obligations.saveAndFlush(new Obligation("Legacy partial", "Legacy fixture", "Site",
                operator.getId(), NOW.plusSeconds(3600), 1L, 1L));
        Execution legacy = executions.saveAndFlush(new Execution(obligation.getId(), operator.getId(), ExecutionResult.PARTIAL, "Legacy"));
        var page = query(null, null, null, null, null, 50, operator);
        // El constructor legacy usa el reloj del sistema; fijamos el corte después de ese instante.
        clock.set(legacy.getExecutedAt().plusSeconds(60));
        page = query(null, null, null, null, null, 50, operator);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).state()).isEqualTo(ExecutionState.LEGACY);
        assertThat(page.items().get(0).recordedAt()).isBetween(legacy.getExecutedAt().minusMillis(1), legacy.getExecutedAt().plusMillis(1));
        assertThat(page.items().get(0).result()).isEqualTo(ExecutionResult.PARTIAL);
        assertThat(page.items().get(0).complianceResult()).isNull();
    }

    @Test void httpRequiresAuthenticatedActiveOperatorAndValidFilters() throws Exception {
        submitted(operator, "Own", NOW.minusSeconds(100));
        mvc.perform(get("/api/v1/executions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/executions").header("Authorization", "Bearer " + jwt.createToken(supervisor)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/executions").header("Authorization", "Bearer " + jwt.createToken(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].activity").value("Own"))
                .andExpect(jsonPath("$.items[0].synchronizationState").value("SYNCED"))
                .andExpect(jsonPath("$.items[0].complianceResult").isEmpty());
        mvc.perform(get("/api/v1/executions").header("Authorization", "Bearer " + jwt.createToken(operator)).param("limit", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/executions").header("Authorization", "Bearer " + jwt.createToken(operator)).param("reviewStatus", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> query(LocalDate.of(2030, 1, 3), LocalDate.of(2030, 1, 2), null, null, null, 50, operator))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> query(null, null, "x".repeat(201), null, null, 50, operator))
                .isInstanceOf(FieldValidationException.class);
        operator.suspend(); users.saveAndFlush(operator);
        assertThatThrownBy(() -> query(null, null, null, null, null, 50, operator)).isInstanceOf(AccessDeniedException.class);
    }

    private OperatorHistoryQueryService.HistoryPage query(LocalDate from, LocalDate to, String search,
            HistoryReviewStatus status, String cursor, int limit, User actor) {
        return history.query(from, to, search, status, cursor, limit, actor);
    }
    private User user(Role role, Long company, Long site) {
        return users.saveAndFlush(new User("History fixture", UUID.randomUUID() + "@example.test",
                passwords.encode("Password123!"), role, company, site));
    }
    private Obligation obligation(User assignee, String title, Long company, Long site) {
        Obligation obligation = Obligation.configured(title, "History fixture", "Site", NOW.minusSeconds(7200),
                NOW.plusSeconds(7200), company, site, List.of(), List.of());
        obligation.assignTo(assignee.getId());
        return obligations.saveAndFlush(obligation);
    }
    private Execution submitted(User assignee, String title, Instant submittedAt) {
        Obligation obligation = obligation(assignee, title, assignee.getCompanyId(), assignee.getSiteId());
        Execution execution = Execution.start(obligation.getId(), assignee.getId(), submittedAt.minusSeconds(120));
        execution.submit("Submitted", submittedAt);
        return executions.saveAndFlush(execution);
    }
    private void decide(Execution execution, ComplianceOutcome outcome, Instant at) {
        var obligation = obligations.findById(execution.getObligationId()).orElseThrow();
        ComplianceCase complianceCase = cases.findByObligationId(obligation.getId()).orElseGet(() ->
                cases.saveAndFlush(new ComplianceCase(obligation.getId(), obligation.getCompanyId(), obligation.getSiteId(), supervisor.getId(), at)));
        Long predecessor = results.findFirstByComplianceCaseIdOrderByDecisionVersionDesc(complianceCase.getId())
                .map(ComplianceResult::getId).orElse(null);
        long version = complianceCase.appendDecision(at); cases.saveAndFlush(complianceCase);
        results.saveAndFlush(new ComplianceResult(complianceCase.getId(), execution.getId(), version, predecessor, outcome,
                "execution-" + execution.getId() + "-v" + execution.getVersion(), execution.getVersion(), null, null, null, null,
                "Actual supervisor decision", predecessor == null ? null : "Reevaluation", supervisor.getId(), at, List.of(), List.of()));
    }
    @TestConfiguration static class ClockConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        void set(Instant value) { now.set(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
