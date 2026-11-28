package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.SiteScope;
import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.SiteScopeRepository;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.infrastructure.security.JwtService;
import com.api.servicecompliance.compliance.application.ComplianceDecisionService;
import com.api.servicecompliance.compliance.application.ClientObservationService;
import com.api.servicecompliance.compliance.domain.model.ClientObservationOrigin;
import com.api.servicecompliance.compliance.domain.model.ComplianceOutcome;
import com.api.servicecompliance.compliance.domain.model.CriterionStatus;
import com.api.servicecompliance.compliance.domain.model.ExceptionDisposition;
import com.api.servicecompliance.compliance.domain.repository.ComplianceCaseRepository;
import com.api.servicecompliance.compliance.domain.repository.ComplianceResultRepository;
import com.api.servicecompliance.compliance.domain.repository.ClientObservationRepository;
import com.api.servicecompliance.executions.domain.model.Evidence;
import com.api.servicecompliance.executions.domain.model.ExceptionReason;
import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.model.ExecutionStepProgress;
import com.api.servicecompliance.executions.domain.model.OperationalException;
import com.api.servicecompliance.executions.domain.repository.EvidenceRepository;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.executions.domain.repository.ExecutionStepProgressRepository;
import com.api.servicecompliance.executions.domain.repository.OperationalExceptionEvidenceRepository;
import com.api.servicecompliance.executions.domain.repository.OperationalExceptionRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.model.ObligationEvidenceRequirement;
import com.api.servicecompliance.obligations.domain.model.ObligationStep;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.serviceplanning.domain.model.CompanyService;
import com.api.servicecompliance.serviceplanning.domain.model.EvidenceRequirement;
import com.api.servicecompliance.serviceplanning.domain.model.EvidenceRequirementType;
import com.api.servicecompliance.serviceplanning.domain.model.ObligationDefinition;
import com.api.servicecompliance.serviceplanning.domain.model.RecurrenceType;
import com.api.servicecompliance.serviceplanning.domain.model.ServicePlan;
import com.api.servicecompliance.serviceplanning.domain.repository.CompanyServiceRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ObligationDefinitionRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.FieldValidationException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ComplianceDecisionIntegrationTests.ClockConfiguration.class)
class ComplianceDecisionIntegrationTests {
    private static final Instant NOW = Instant.parse("2031-03-04T15:00:00Z");

    @Autowired ComplianceDecisionService decisions;
    @Autowired ClientObservationService clientObservations;
    @Autowired ClientObservationRepository clientObservationRepository;
    @Autowired ComplianceCaseRepository cases;
    @Autowired ComplianceResultRepository results;
    @Autowired ObligationRepository obligations;
    @Autowired ExecutionRepository executions;
    @Autowired ExecutionStepProgressRepository progress;
    @Autowired EvidenceRepository evidence;
    @Autowired OperationalExceptionRepository exceptions;
    @Autowired OperationalExceptionEvidenceRepository exceptionEvidence;
    @Autowired ObligationDefinitionRepository definitions;
    @Autowired ServicePlanRepository plans;
    @Autowired CompanyServiceRepository companyServices;
    @Autowired SiteScopeRepository sites;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private User supervisor;
    private User operator;
    private CompanyService companyService;
    private ServicePlan plan;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        supervisor = user("Supervisor", Role.SUPERVISOR, 1L, 1L);
        operator = user("Operator", Role.OPERATOR, 1L, 1L);
        companyService = companyServices.saveAndFlush(new CompanyService(
                1L, "DECISION-" + UUID.randomUUID(), "Compliance decision"));
        plan = plans.saveAndFlush(new ServicePlan(1L, companyService.getId(), 1L,
                LocalDate.of(2031, 3, 1), LocalDate.of(2031, 3, 31), supervisor.getId(),
                NOW.minusSeconds(86_400)));
    }

    @AfterEach
    void cleanUp() {
        results.findAll().stream()
                .sorted(java.util.Comparator.comparingLong(
                        com.api.servicecompliance.compliance.domain.model.ComplianceResult::getDecisionVersion)
                        .reversed())
                .forEach(results::delete);
        results.flush();
        clientObservationRepository.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(
                        com.api.servicecompliance.compliance.domain.model.ClientObservation::getRevision)
                        .reversed())
                .forEach(clientObservationRepository::delete);
        clientObservationRepository.flush();
        cases.deleteAll();
        exceptionEvidence.deleteAll();
        exceptions.deleteAll();
        evidence.deleteAll();
        progress.deleteAll();
        executions.deleteAll();
        obligations.deleteAll();
        definitions.deleteAll();
        plans.deleteAll();
        if (companyService != null && companyService.getId() != null) {
            companyServices.findById(companyService.getId()).ifPresent(companyServices::delete);
        }
        users.deleteAll();
        sites.findById(2L).ifPresent(sites::delete);
    }

    @Test
    void recordsAcceptedExceptionWithoutChangingSourceFacts() throws Exception {
        Scenario scenario = scenario(true, true);
        long executionVersion = scenario.execution().getVersion();
        long evidenceCount = evidence.count();
        long exceptionCount = exceptions.count();
        var command = acceptedExceptionCommand(scenario, 0L, null);

        mvc.perform(post("/api/v1/compliance-reviews/executions/{id}/results",
                        scenario.execution().getId())
                        .header("Authorization", "Bearer " + jwt.createToken(supervisor))
                        .contentType("application/json")
                        .content(json.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseVersion").value(1))
                .andExpect(jsonPath("$.outcome").value("EXCEPTION_ACCEPTED"))
                .andExpect(jsonPath("$.sourceReviewVersion").value(scenario.reviewVersion()))
                .andExpect(jsonPath("$.sourcePlanVersion").value(1))
                .andExpect(jsonPath("$.criteria[0].status").value("PASS"))
                .andExpect(jsonPath("$.findings[0].exceptionId").value(scenario.exceptionId()))
                .andExpect(jsonPath("$.findings[0].exceptionDisposition").value("ACCEPTED"));

        assertThat(executions.findById(scenario.execution().getId()).orElseThrow().getVersion())
                .isEqualTo(executionVersion);
        assertThat(evidence.count()).isEqualTo(evidenceCount);
        assertThat(exceptions.count()).isEqualTo(exceptionCount);
    }

    @Test
    void appendsReevaluationAndRejectsStaleCaseVersion() {
        Scenario scenario = scenario(true, false);
        var first = decisions.decide(scenario.execution().getId(), compliantCommand(scenario, 0L), supervisor);
        var reevaluation = new ComplianceDecisionService.DecisionCommand(
                ComplianceOutcome.NON_COMPLIANT, scenario.reviewVersion(), 1L,
                List.of(new ComplianceDecisionService.CriterionDecision(
                        "SERVICE_QUALITY", CriterionStatus.FAIL, "Quality threshold not met")),
                List.of(), "The submitted evidence does not meet the quality threshold",
                "New supervisor observation");

        var second = decisions.decide(scenario.execution().getId(), reevaluation, supervisor);

        assertThat(second.caseId()).isEqualTo(first.caseId());
        assertThat(second.caseVersion()).isEqualTo(2);
        assertThat(second.predecessorResultId()).isEqualTo(first.resultId());
        assertThat(second.reevaluationReason()).isEqualTo("New supervisor observation");
        assertThat(results.findById(first.resultId()).orElseThrow().getOutcome())
                .isEqualTo(ComplianceOutcome.COMPLIANT);
        assertThat(results.count()).isEqualTo(2);

        assertThatThrownBy(() -> decisions.decide(scenario.execution().getId(), reevaluation, supervisor))
                .isInstanceOf(ApiConflictException.class)
                .extracting(error -> ((ApiConflictException) error).getCode())
                .isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void recordsAndAmendsClientObservationWithoutOverwritingHistory() throws Exception {
        Scenario scenario = scenario(true, false);
        var initial = decisions.decide(scenario.execution().getId(), compliantCommand(scenario, 0L), supervisor);
        var created = clientObservations.create(initial.caseId(),
                new ClientObservationService.CreateObservationCommand(
                        "Client reported a quality issue", ClientObservationOrigin.PHONE,
                        LocalDate.of(2031, 3, 4)), supervisor);

        var amended = clientObservations.amend(created.id(),
                new ClientObservationService.AmendmentCommand(1,
                        "Client confirmed the quality issue", ClientObservationOrigin.EMAIL,
                        LocalDate.of(2031, 3, 3), "Corrected origin and wording"), supervisor);

        assertThat(amended.revision()).isEqualTo(2);
        assertThat(amended.supersedesObservationId()).isEqualTo(created.id());
        assertThat(clientObservations.list(initial.caseId(), supervisor)).hasSize(2);
        assertThat(clientObservationRepository.findById(created.id()).orElseThrow().getDescription())
                .isEqualTo("Client reported a quality issue");

        assertThatThrownBy(() -> clientObservations.amend(created.id(),
                new ClientObservationService.AmendmentCommand(1, "Stale", ClientObservationOrigin.OTHER,
                        LocalDate.of(2031, 3, 4), "Stale amendment"), supervisor))
                .isInstanceOf(ApiConflictException.class)
                .extracting(error -> ((ApiConflictException) error).getCode())
                .isEqualTo("OBSERVATION_NOT_LATEST");

        mvc.perform(get("/api/v1/compliance-cases/{id}/observations", initial.caseId())
                        .header("Authorization", "Bearer " + jwt.createToken(supervisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revision").value(1))
                .andExpect(jsonPath("$[1].revision").value(2));
    }

    @Test
    void reevaluatesOnceFromObservationAndPreservesOriginalResult() {
        Scenario scenario = scenario(true, false);
        var initial = decisions.decide(scenario.execution().getId(), compliantCommand(scenario, 0L), supervisor);
        var observation = clientObservations.create(initial.caseId(),
                new ClientObservationService.CreateObservationCommand(
                        "Client rejected the delivered service", ClientObservationOrigin.SITE_VISIT,
                        LocalDate.of(2031, 3, 4)), supervisor);
        var command = new ComplianceDecisionService.ObservationReevaluationCommand(
                ComplianceOutcome.NON_COMPLIANT, scenario.reviewVersion(), 1L,
                List.of(new ComplianceDecisionService.CriterionDecision(
                        "SERVICE_QUALITY", CriterionStatus.FAIL, "Client feedback confirmed failure")),
                List.of(), "Client feedback changes the compliance conclusion");

        var reevaluated = clientObservations.reevaluate(observation.id(), command, supervisor);

        assertThat(reevaluated.caseVersion()).isEqualTo(2);
        assertThat(reevaluated.sourceObservationId()).isEqualTo(observation.id());
        assertThat(reevaluated.reevaluationReason())
                .isEqualTo("Client observation #" + observation.id()
                        + ": Client rejected the delivered service");
        assertThat(results.findById(initial.resultId()).orElseThrow().getOutcome())
                .isEqualTo(ComplianceOutcome.COMPLIANT);

        var replay = new ComplianceDecisionService.ObservationReevaluationCommand(
                command.outcome(), command.reviewVersion(), 2L, command.criteria(),
                command.findings(), command.explanation());
        assertThatThrownBy(() -> clientObservations.reevaluate(observation.id(), replay, supervisor))
                .isInstanceOf(ApiConflictException.class)
                .extracting(error -> ((ApiConflictException) error).getCode())
                .isEqualTo("OBSERVATION_ALREADY_EVALUATED");
    }

    @Test
    void rejectsFutureObservationWrongRoleAndOtherSiteScope() {
        Scenario scenario = scenario(true, false);
        var initial = decisions.decide(scenario.execution().getId(), compliantCommand(scenario, 0L), supervisor);
        var future = new ClientObservationService.CreateObservationCommand(
                "Future report", ClientObservationOrigin.OTHER, LocalDate.of(2031, 3, 5));

        assertThatThrownBy(() -> clientObservations.create(initial.caseId(), future, supervisor))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> clientObservations.create(initial.caseId(),
                new ClientObservationService.CreateObservationCommand(
                        "Unauthorized", ClientObservationOrigin.OTHER, LocalDate.of(2031, 3, 4)), operator))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        if (!sites.existsById(2L)) sites.saveAndFlush(new SiteScope(2L, 1L, "Other site"));
        User otherSite = user("Other site supervisor", Role.SUPERVISOR, 1L, 2L);
        assertThatThrownBy(() -> clientObservations.create(initial.caseId(),
                new ClientObservationService.CreateObservationCommand(
                        "Out of scope", ClientObservationOrigin.EMAIL, LocalDate.of(2031, 3, 4)), otherSite))
                .isInstanceOf(com.api.servicecompliance.shared.domain.ResourceNotFoundException.class);
    }

    @Test
    void enforcesOutcomeEligibilityAndCompleteReview() {
        Scenario exceptionScenario = scenario(true, true);
        var invalidCompliant = compliantCommand(exceptionScenario, 0L);

        assertThatThrownBy(() -> decisions.decide(
                exceptionScenario.execution().getId(), invalidCompliant, supervisor))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("no es válida");
        assertThat(cases.count()).isZero();

        Scenario incomplete = scenario(false, false);
        assertThatThrownBy(() -> decisions.decide(
                incomplete.execution().getId(), compliantCommand(incomplete, 0L), supervisor))
                .isInstanceOf(ApiConflictException.class)
                .extracting(error -> ((ApiConflictException) error).getCode())
                .isEqualTo("REVIEW_INCOMPLETE");
        assertThat(cases.count()).isZero();
    }

    @Test
    void rejectsUnknownExceptionAndStaleReviewVersion() {
        Scenario scenario = scenario(true, true);
        var unknownException = new ComplianceDecisionService.DecisionCommand(
                ComplianceOutcome.EXCEPTION_ACCEPTED, scenario.reviewVersion(), 0L,
                List.of(new ComplianceDecisionService.CriterionDecision(
                        "SERVICE_QUALITY", CriterionStatus.PASS, null)),
                List.of(new ComplianceDecisionService.FindingDecision(
                        "EXCEPTION_REVIEW", "Accepted", Long.MAX_VALUE,
                        ExceptionDisposition.ACCEPTED)), "Accepted documented exception", null);

        assertThatThrownBy(() -> decisions.decide(
                scenario.execution().getId(), unknownException, supervisor))
                .isInstanceOf(FieldValidationException.class);

        var staleReview = new ComplianceDecisionService.DecisionCommand(
                ComplianceOutcome.EXCEPTION_ACCEPTED, "execution-0-v0", 0L,
                acceptedExceptionCommand(scenario, 0L, null).criteria(),
                acceptedExceptionCommand(scenario, 0L, null).findings(), "Accepted", null);
        assertThatThrownBy(() -> decisions.decide(scenario.execution().getId(), staleReview, supervisor))
                .isInstanceOf(ApiConflictException.class)
                .extracting(error -> ((ApiConflictException) error).getCode())
                .isEqualTo("REVIEW_VERSION_CONFLICT");
    }

    @Test
    void enforcesRoleAndSiteScope() throws Exception {
        Scenario scenario = scenario(true, false);
        if (!sites.existsById(2L)) sites.saveAndFlush(new SiteScope(2L, 1L, "Other site"));
        User otherSite = user("Other site supervisor", Role.SUPERVISOR, 1L, 2L);
        String body = json.writeValueAsString(compliantCommand(scenario, 0L));

        mvc.perform(post("/api/v1/compliance-reviews/executions/{id}/results",
                        scenario.execution().getId())
                        .header("Authorization", "Bearer " + jwt.createToken(operator))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/compliance-reviews/executions/{id}/results",
                        scenario.execution().getId())
                        .header("Authorization", "Bearer " + jwt.createToken(otherSite))
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        assertThat(cases.count()).isZero();
    }

    @Test
    void serializesConcurrentFirstDecision() throws Exception {
        Scenario scenario = scenario(true, false);
        var command = compliantCommand(scenario, 0L);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> concurrentDecision(start, scenario, command));
            Future<String> second = executor.submit(() -> concurrentDecision(start, scenario, command));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("CREATED", "VERSION_CONFLICT:1");
        } finally {
            executor.shutdownNow();
        }
        assertThat(cases.count()).isEqualTo(1);
        assertThat(results.count()).isEqualTo(1);
    }

    private String concurrentDecision(CountDownLatch start, Scenario scenario,
                                      ComplianceDecisionService.DecisionCommand command) throws Exception {
        start.await();
        try {
            decisions.decide(scenario.execution().getId(), command, supervisor);
            return "CREATED";
        } catch (ApiConflictException conflict) {
            return conflict.getCode() + ":" + conflict.getCurrentVersion();
        }
    }

    private ComplianceDecisionService.DecisionCommand compliantCommand(Scenario scenario, long expectedVersion) {
        return new ComplianceDecisionService.DecisionCommand(
                ComplianceOutcome.COMPLIANT, scenario.reviewVersion(), expectedVersion,
                List.of(new ComplianceDecisionService.CriterionDecision(
                        "SERVICE_QUALITY", CriterionStatus.PASS, "Threshold met")),
                List.of(), "All applicable requirements were met", null);
    }

    private ComplianceDecisionService.DecisionCommand acceptedExceptionCommand(
            Scenario scenario, long expectedVersion, String reevaluationReason) {
        return new ComplianceDecisionService.DecisionCommand(
                ComplianceOutcome.EXCEPTION_ACCEPTED, scenario.reviewVersion(), expectedVersion,
                List.of(new ComplianceDecisionService.CriterionDecision(
                        "SERVICE_QUALITY", CriterionStatus.PASS, "Threshold met")),
                List.of(new ComplianceDecisionService.FindingDecision(
                        "EXCEPTION_REVIEW", "The documented exception is justified",
                        scenario.exceptionId(), ExceptionDisposition.ACCEPTED)),
                "The exception was supported by the submitted evidence", reevaluationReason);
    }

    private Scenario scenario(boolean complete, boolean withException) {
        EvidenceRequirement sourceRequirement = new EvidenceRequirement(
                EvidenceRequirementType.QR_CODE, true, 1, 1, Map.of("expectedPrefix", "QR-"));
        ObligationDefinition definition = definitions.saveAndFlush(new ObligationDefinition(plan.getId(), 1L,
                "Original activity", "Original instructions", "Original acceptance", RecurrenceType.DAILY,
                null, 1, Set.of(), LocalTime.of(8, 0), 60, List.of(sourceRequirement),
                supervisor.getId(), NOW.minusSeconds(86_000)));
        Long sourceRequirementId = definition.getEvidenceRequirements().get(0).getId();
        ObligationEvidenceRequirement requirement = new ObligationEvidenceRequirement(sourceRequirementId,
                com.api.servicecompliance.executions.domain.model.EvidenceType.QR_CODE,
                true, 1, 1, 1, Map.of("expectedPrefix", "QR-"));
        Obligation obligation = Obligation.generated("Original activity", "Original instructions", "Main site",
                NOW.minusSeconds(3_600), NOW.plusSeconds(3_600), 1L, 1L, plan.getId(),
                plan.getBusinessVersion(), definition.getId(), definition.getVersion(),
                LocalDate.of(2031, 3, 4), List.of(new ObligationStep("Clean", "Clean carefully", 1)),
                List.of(requirement));
        obligation.assignTo(operator.getId());
        obligation.markInProgress();
        obligation = obligations.saveAndFlush(obligation);

        Execution execution = executions.saveAndFlush(Execution.start(
                obligation.getId(), operator.getId(), NOW.minusSeconds(3_000)));
        ExecutionStepProgress storedProgress = new ExecutionStepProgress(execution.getId(),
                obligation.getSteps().get(0).getId(), NOW.minusSeconds(2_900));
        Long exceptionId = null;
        if (complete) {
            storedProgress.update(true, NOW.minusSeconds(2_800));
            progress.saveAndFlush(storedProgress);
            execution.stepChanged(true, NOW.minusSeconds(2_800));
            Evidence storedEvidence = evidence.saveAndFlush(Evidence.qrCode(execution.getId(),
                    obligation.getEvidenceRequirements().get(0).getId(), operator.getId(), "QR-123", 6,
                    "a".repeat(64), NOW.minusSeconds(2_700), NOW.minusSeconds(2_600)));
            execution.evidenceAttached(NOW.minusSeconds(2_600));
            if (withException) {
                OperationalException storedException = exceptions.saveAndFlush(new OperationalException(
                        execution.getId(), obligation.getSteps().get(0).getId(), ExceptionReason.OTHER,
                        "Documented issue", operator.getId(), NOW.minusSeconds(2_500), UUID.randomUUID()));
                exceptionId = storedException.getId();
                execution.exceptionReported(NOW.minusSeconds(2_500));
            }
        } else {
            progress.saveAndFlush(storedProgress);
        }
        execution.submit("Submitted for review", NOW.minusSeconds(2_000));
        obligation.markSubmitted();
        obligations.saveAndFlush(obligation);
        execution = executions.saveAndFlush(execution);
        return new Scenario(execution, exceptionId,
                "execution-" + execution.getId() + "-v" + execution.getVersion());
    }

    private User user(String name, Role role, Long companyId, Long siteId) {
        return users.saveAndFlush(new User(name, UUID.randomUUID() + "@example.test",
                passwords.encode("Password123!"), role, companyId, siteId));
    }

    private record Scenario(Execution execution, Long exceptionId, String reviewVersion) {}

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        void set(Instant value) { now.set(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
