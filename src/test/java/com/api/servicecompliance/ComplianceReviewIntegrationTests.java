package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.SiteScope;
import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.SiteScopeRepository;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.infrastructure.security.JwtService;
import com.api.servicecompliance.compliance.application.ComplianceReviewService;
import com.api.servicecompliance.executions.application.RequiredEvidenceService;
import com.api.servicecompliance.executions.domain.model.Evidence;
import com.api.servicecompliance.executions.domain.model.ExceptionReason;
import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.model.ExecutionStepProgress;
import com.api.servicecompliance.executions.domain.model.OperationalException;
import com.api.servicecompliance.executions.domain.model.OperationalExceptionEvidence;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ComplianceReviewIntegrationTests.ClockConfiguration.class)
class ComplianceReviewIntegrationTests {
    private static final Instant NOW = Instant.parse("2031-02-03T15:00:00Z");

    @Autowired ComplianceReviewService reviews;
    @Autowired RequiredEvidenceService requiredEvidence;
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
                1L, "REVIEW-" + UUID.randomUUID(), "Compliance review"));
        plan = plans.saveAndFlush(new ServicePlan(1L, companyService.getId(), 1L,
                LocalDate.of(2031, 2, 1), LocalDate.of(2031, 2, 28), supervisor.getId(),
                NOW.minusSeconds(86_400)));
    }

    @AfterEach
    void cleanUp() {
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
        sites.findById(3L).ifPresent(sites::delete);
        if (hasCompaniesTable()) jdbc.update("DELETE FROM companies WHERE id = ?", 2L);
    }

    @Test
    void returnsFrozenHistoricalReviewWithoutPrivateStorageKeys() throws Exception {
        Scenario scenario = scenario(true, true);
        scenario.definition().update("Changed later", "Changed", "Changed", RecurrenceType.DAILY,
                null, 1, Set.of(), LocalTime.of(9, 0), 60,
                List.of(new EvidenceRequirement(EvidenceRequirementType.LOCATION, true, 1, 1, Map.of())),
                supervisor.getId(), NOW.plusSeconds(60));
        definitions.saveAndFlush(scenario.definition());

        String token = "Bearer " + jwt.createToken(supervisor);
        mvc.perform(get("/api/v1/compliance-reviews/executions/{id}", scenario.execution().getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewVersion").value(
                        "execution-" + scenario.execution().getId() + "-v" + scenario.execution().getVersion()))
                .andExpect(jsonPath("$.obligation.title").value("Original activity"))
                .andExpect(jsonPath("$.obligation.sourcePlanVersion").value(1))
                .andExpect(jsonPath("$.obligation.sourceDefinitionVersion").value(
                        scenario.sourceDefinitionVersion()))
                .andExpect(jsonPath("$.obligation.evidenceRequirements[0].type").value("QR_CODE"))
                .andExpect(jsonPath("$.execution.operator.fullName").value("Operator"))
                .andExpect(jsonPath("$.evidence[0].textValue").value("QR-123"))
                .andExpect(jsonPath("$.evidence[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.exceptions[0].evidenceIds[0]").value(
                        scenario.evidence().getId()))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.missingInformation").isEmpty());

        assertThatThrownBy(() -> requiredEvidence.attachStructured(scenario.execution().getId(),
                scenario.requirementId(), "reviewlate01",
                new RequiredEvidenceService.StructuredEvidenceCommand("QR-LATE", null, null, null,
                        NOW, scenario.execution().getVersion()), operator))
                .isInstanceOf(ApiConflictException.class)
                .hasMessageContaining("enviada");
        assertThat(reviews.review(scenario.execution().getId(), supervisor).reviewVersion())
                .isEqualTo("execution-" + scenario.execution().getId() + "-v"
                        + scenario.execution().getVersion());
    }

    @Test
    void identifiesMissingInformationWithoutCreatingAComplianceResult() throws Exception {
        Scenario scenario = scenario(false, true);

        mvc.perform(get("/api/v1/compliance-reviews/executions/{id}", scenario.execution().getId())
                        .header("Authorization", "Bearer " + jwt.createToken(supervisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.missingInformation[0].category").value("EVIDENCE_REQUIREMENT"))
                .andExpect(jsonPath("$.missingInformation[0].code").value("MINIMUM_EVIDENCE_NOT_MET"))
                .andExpect(jsonPath("$.missingInformation[1].category").value("STEP"))
                .andExpect(jsonPath("$.complianceResult").doesNotExist());
    }

    @Test
    void rejectsExecutionsThatHaveNotBeenSubmitted() throws Exception {
        Scenario scenario = scenario(false, false);

        mvc.perform(get("/api/v1/compliance-reviews/executions/{id}", scenario.execution().getId())
                        .header("Authorization", "Bearer " + jwt.createToken(supervisor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_READY"))
                .andExpect(jsonPath("$.currentVersion").value(scenario.execution().getVersion()));
    }

    @Test
    void enforcesRoleSiteAndCompanyIsolation() throws Exception {
        Scenario scenario = scenario(true, true);
        if (!sites.existsById(2L)) sites.saveAndFlush(new SiteScope(2L, 1L, "Other site"));
        User otherSite = user("Other site supervisor", Role.SUPERVISOR, 1L, 2L);
        long otherCompanySiteId = 1L;
        if (hasCompaniesTable()) {
            jdbc.update("INSERT INTO companies(id, name) VALUES (?, ?)", 2L, "Other company");
            sites.saveAndFlush(new SiteScope(3L, 2L, "Other company site"));
            otherCompanySiteId = 3L;
        }
        User otherCompany = user("Other company supervisor", Role.SUPERVISOR, 2L, otherCompanySiteId);

        mvc.perform(get("/api/v1/compliance-reviews/executions/{id}", scenario.execution().getId())
                        .header("Authorization", "Bearer " + jwt.createToken(operator)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/compliance-reviews/executions/{id}", scenario.execution().getId())
                        .header("Authorization", "Bearer " + jwt.createToken(otherSite)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/compliance-reviews/executions/{id}", scenario.execution().getId())
                        .header("Authorization", "Bearer " + jwt.createToken(otherCompany)))
                .andExpect(status().isNotFound());
    }

    private Scenario scenario(boolean complete, boolean submitted) {
        EvidenceRequirement sourceRequirement = new EvidenceRequirement(
                EvidenceRequirementType.QR_CODE, true, 1, 1, Map.of("expectedPrefix", "QR-"));
        ObligationDefinition definition = definitions.saveAndFlush(new ObligationDefinition(plan.getId(), 1L,
                "Original activity", "Original instructions", "Original acceptance", RecurrenceType.DAILY,
                null, 1, Set.of(), LocalTime.of(8, 0), 60, List.of(sourceRequirement),
                supervisor.getId(), NOW.minusSeconds(86_000)));
        long sourceDefinitionVersion = definition.getVersion();
        Long sourceRequirementId = definition.getEvidenceRequirements().get(0).getId();
        ObligationEvidenceRequirement requirement = new ObligationEvidenceRequirement(sourceRequirementId,
                com.api.servicecompliance.executions.domain.model.EvidenceType.QR_CODE,
                true, 1, 1, 1, Map.of("expectedPrefix", "QR-"));
        Obligation obligation = Obligation.generated("Original activity", "Original instructions", "Main site",
                NOW.minusSeconds(3_600), NOW.plusSeconds(3_600), 1L, 1L, plan.getId(),
                plan.getBusinessVersion(), definition.getId(), sourceDefinitionVersion,
                LocalDate.of(2031, 2, 3), List.of(new ObligationStep("Clean", "Clean carefully", 1)),
                List.of(requirement));
        obligation.assignTo(operator.getId());
        obligation.markInProgress();
        obligation = obligations.saveAndFlush(obligation);

        Execution execution = executions.saveAndFlush(Execution.start(
                obligation.getId(), operator.getId(), NOW.minusSeconds(3_000)));
        ExecutionStepProgress storedProgress = new ExecutionStepProgress(execution.getId(),
                obligation.getSteps().get(0).getId(), NOW.minusSeconds(2_900));
        Evidence storedEvidence = null;
        if (complete) {
            storedProgress.update(true, NOW.minusSeconds(2_800));
            progress.saveAndFlush(storedProgress);
            execution.stepChanged(true, NOW.minusSeconds(2_800));
            Long requirementId = obligation.getEvidenceRequirements().get(0).getId();
            storedEvidence = evidence.saveAndFlush(Evidence.qrCode(execution.getId(), requirementId,
                    operator.getId(), "QR-123", 6, "a".repeat(64), NOW.minusSeconds(2_700),
                    NOW.minusSeconds(2_600)));
            execution.evidenceAttached(NOW.minusSeconds(2_600));
            OperationalException storedException = exceptions.saveAndFlush(new OperationalException(
                    execution.getId(), obligation.getSteps().get(0).getId(), ExceptionReason.OTHER,
                    "Minor issue", operator.getId(), NOW.minusSeconds(2_500), UUID.randomUUID()));
            exceptionEvidence.saveAndFlush(new OperationalExceptionEvidence(storedException.getId(),
                    storedEvidence.getId(), execution.getId()));
            execution.exceptionReported(NOW.minusSeconds(2_500));
        } else {
            progress.saveAndFlush(storedProgress);
        }
        if (submitted) {
            execution.submit("Submitted for review", NOW.minusSeconds(2_000));
            obligation.markSubmitted();
            obligations.saveAndFlush(obligation);
        }
        execution = executions.saveAndFlush(execution);
        return new Scenario(definition, execution, storedEvidence,
                obligation.getEvidenceRequirements().get(0).getId(), sourceDefinitionVersion);
    }

    private User user(String name, Role role, Long companyId, Long siteId) {
        return users.saveAndFlush(new User(name, UUID.randomUUID() + "@example.test",
                passwords.encode("Password123!"), role, companyId, siteId));
    }

    private boolean hasCompaniesTable() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE UPPER(table_name) = 'COMPANIES'
                """, Integer.class);
        return count != null && count > 0;
    }

    private record Scenario(ObligationDefinition definition, Execution execution, Evidence evidence,
                            Long requirementId, long sourceDefinitionVersion) {}

    @TestConfiguration
    static class ClockConfiguration {
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
