package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.*;
import com.api.servicecompliance.authentication.domain.repository.*;
import com.api.servicecompliance.authentication.infrastructure.persistence.*;
import com.api.servicecompliance.serviceplanning.application.*;
import com.api.servicecompliance.serviceplanning.domain.model.*;
import com.api.servicecompliance.serviceplanning.domain.repository.*;
import com.api.servicecompliance.shared.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ServicePlanActivationIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository users;
    @Autowired SiteScopeRepository sites;
    @Autowired CompanyServiceRepository companyServices;
    @Autowired ServicePlanRepository plans;
    @Autowired ServicePlanIdempotencyRepository idempotency;
    @Autowired ObligationDefinitionRepository definitions;
    @Autowired RefreshSessionRepository refreshSessions;
    @Autowired RefreshFamilyRepository refreshFamilies;
    @Autowired ServicePlanService servicePlans;
    @Autowired ObligationDefinitionService definitionService;
    @Autowired ServicePlanActivationService activation;
    private final ObjectMapper json = new ObjectMapper();

    private User administrator;
    private Long serviceId;

    @BeforeEach
    void setUp() {
        clean();
        sites.deleteAll();
        users.deleteAll();
        sites.save(new SiteScope(1L, 1L, "Legacy site"));
        administrator = users.save(new User("Administrator", "activation-admin@test.com",
                passwordEncoder.encode("Password123!"), Role.ADMIN, 1L, 1L));
        serviceId = companyServices.save(new CompanyService(1L, "CLEANING", "Cleaning")).getId();
    }

    @AfterEach
    void tearDown() {
        clean();
        users.deleteAll();
        sites.deleteAll();
    }

    @Test
    void activationRequiresCompleteDraftAndIsIdempotent() {
        var incomplete = createPlan("activation-incomplete", "2026-11-01", "2026-12-31");
        assertThatThrownBy(() -> activation.activate(incomplete.id(), command(incomplete.version()), administrator))
                .isInstanceOf(ApiConflictException.class)
                .satisfies(error -> assertThat(((ApiConflictException) error).getCode())
                        .isEqualTo("ACTIVATION_REQUIREMENTS_MISSING"));

        var draft = createCompletePlan("activation-complete", "2026-11-01", "2026-12-31");
        var active = activation.activate(draft.id(), command(draft.version()), administrator);
        var replay = activation.activate(draft.id(), command(draft.version()), administrator);
        assertThat(active.state()).isEqualTo(ServicePlanState.ACTIVE);
        assertThat(active.activatedBy()).isEqualTo(administrator.getId());
        assertThat(active.activatedAt()).isNotNull().isEqualTo(replay.activatedAt());
        assertThat(replay.id()).isEqualTo(active.id());
    }

    @Test
    void amendmentClonesHistoryAndSuccessorActivationSupersedesPredecessor() {
        var originalDraft = createCompletePlan("version-original", "2026-11-01", "2026-12-31");
        var original = activation.activate(originalDraft.id(), command(originalDraft.version()), administrator);
        var amendment = activation.amend(original.id(), command(original.version()), administrator);

        assertThat(amendment.seriesId()).isEqualTo(original.seriesId());
        assertThat(amendment.businessVersion()).isEqualTo(2);
        assertThat(amendment.supersedesPlanId()).isEqualTo(original.id());
        assertThat(amendment.state()).isEqualTo(ServicePlanState.DRAFT);
        var cloned = definitions.findAllByServicePlanId(amendment.id());
        assertThat(cloned).hasSize(1);
        definitionService.update(amendment.id(), cloned.get(0).getId(),
                definitionRequest("Updated future activity", cloned.get(0).getVersion()), administrator);
        assertThat(definitions.findAllByServicePlanId(original.id()).get(0).getActivity())
                .isEqualTo("Original activity");

        var activatedAmendment = activation.activate(amendment.id(), command(amendment.version()), administrator);
        assertThat(activatedAmendment.state()).isEqualTo(ServicePlanState.ACTIVE);
        assertThat(plans.findById(original.id()).orElseThrow().getState())
                .isEqualTo(ServicePlanState.SUPERSEDED);
    }

    @Test
    void concurrentOverlappingSeriesAllowOnlyOneActivation() throws Exception {
        var first = createCompletePlan("overlap-first", "2026-11-01", "2026-12-31");
        var second = createCompletePlan("overlap-second", "2026-12-01", "2027-01-31");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(() -> activateConcurrently(first, ready, start));
            var secondResult = executor.submit(() -> activateConcurrently(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ACTIVE", "PLAN_PERIOD_OVERLAP");
            assertThat(plans.findAll()).filteredOn(ServicePlan::isActive).hasSize(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void authorizationAndHttpContractProtectVersionCommands() throws Exception {
        var draft = createCompletePlan("activation-http", "2026-11-01", "2026-12-31");
        User supervisor = users.save(new User("Supervisor", "activation-supervisor@test.com",
                passwordEncoder.encode("Password123!"), Role.SUPERVISOR, 1L, 1L));
        assertThatThrownBy(() -> activation.activate(draft.id(), command(draft.version()), supervisor))
                .isInstanceOf(AccessDeniedException.class);

        mockMvc.perform(post("/api/v1/service-plans/" + draft.id() + "/activate")
                        .header("Authorization", "Bearer " + login(administrator.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + draft.version() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.businessVersion").value(1));
        mockMvc.perform(get("/api-docs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/activate")))
                .andExpect(content().string(containsString("/amendments")));
    }

    private String activateConcurrently(ServicePlanService.ServicePlanResponse plan,
                                        CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) return "TIMEOUT";
            return activation.activate(plan.id(), command(plan.version()), administrator).state().name();
        } catch (ApiConflictException exception) {
            return exception.getCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "INTERRUPTED";
        }
    }

    private ServicePlanService.ServicePlanResponse createCompletePlan(String key, String start, String end) {
        var plan = createPlan(key, start, end);
        definitionService.create(plan.id(), definitionRequest("Original activity", null), administrator);
        return plan;
    }

    private ServicePlanService.ServicePlanResponse createPlan(String key, String start, String end) {
        return servicePlans.create(new ServicePlanService.CreateServicePlanRequest(serviceId, 1L,
                LocalDate.parse(start), LocalDate.parse(end)), key, administrator);
    }

    private ObligationDefinitionService.DefinitionRequest definitionRequest(String activity, Long version) {
        return new ObligationDefinitionService.DefinitionRequest(1L, activity, "Instructions", "Criteria",
                RecurrenceType.WEEKLY, null, 1, Set.of(DayOfWeek.MONDAY), LocalTime.of(8, 0), 120,
                List.of(new ObligationDefinitionService.EvidenceRequirementRequest(EvidenceRequirementType.PHOTO,
                        true, 1, 2, Map.of("requireGeolocation", "true"))), version);
    }

    private static ServicePlanActivationService.VersionCommand command(long version) {
        return new ServicePlanActivationService.VersionCommand(version);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    private void clean() {
        refreshSessions.deleteAll();
        refreshFamilies.deleteAll();
        definitions.deleteAll();
        idempotency.deleteAll();
        plans.findAll().stream().sorted(Comparator.comparingInt(ServicePlan::getBusinessVersion).reversed())
                .forEach(plans::delete);
        plans.flush();
        companyServices.deleteAll();
    }
}
