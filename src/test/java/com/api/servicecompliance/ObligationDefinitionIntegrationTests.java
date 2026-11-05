package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.*;
import com.api.servicecompliance.authentication.domain.repository.*;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshFamilyRepository;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshSessionRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ObligationDefinitionIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository users;
    @Autowired RefreshSessionRepository refreshSessions;
    @Autowired RefreshFamilyRepository refreshFamilies;
    @Autowired SiteScopeRepository sites;
    @Autowired CompanyServiceRepository companyServices;
    @Autowired ServicePlanRepository plans;
    @Autowired ServicePlanIdempotencyRepository idempotency;
    @Autowired ObligationDefinitionRepository definitions;
    @Autowired ServicePlanService servicePlans;
    @Autowired ObligationDefinitionService definitionService;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    private User administrator;
    private Long planId;

    @BeforeEach
    void setUp() {
        clean();
        sites.deleteAll();
        users.deleteAll();
        sites.save(new SiteScope(1L, 1L, "Legacy site"));
        administrator = users.save(new User("Administrator", "definition-admin@test.com",
                passwordEncoder.encode("Password123!"), Role.ADMIN, 1L, 1L));
        CompanyService cleaning = companyServices.save(new CompanyService(1L, "CLEANING", "Cleaning"));
        planId = servicePlans.create(new ServicePlanService.CreateServicePlanRequest(cleaning.getId(), 1L,
                        LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-31")),
                "definition-plan-key", administrator).id();
    }

    @AfterEach
    void tearDown() {
        clean();
        users.deleteAll();
        sites.deleteAll();
    }

    @Test
    void administratorCreatesAndEditsStructuredDefinitionWithIsolatedEvidenceRules() {
        var first = definitionService.create(planId, weeklyRequest("Clean floors", null,
                EvidenceRequirementType.PHOTO), administrator);
        var second = definitionService.create(planId, weeklyRequest("Scan control point", null,
                EvidenceRequirementType.QR_CODE), administrator);

        assertThat(first.timezone()).isEqualTo("America/Lima");
        assertThat(first.daysOfWeek()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(first.evidenceRequirements()).extracting(ObligationDefinitionService.EvidenceRequirementResponse::type)
                .containsExactly(EvidenceRequirementType.PHOTO);
        assertThat(second.evidenceRequirements()).extracting(ObligationDefinitionService.EvidenceRequirementResponse::type)
                .containsExactly(EvidenceRequirementType.QR_CODE);
        assertThat(servicePlans.find(planId, administrator).missingActivationRequirements()).isEmpty();

        var updated = definitionService.update(planId, first.id(), weeklyRequest("Clean and disinfect", first.version(),
                EvidenceRequirementType.LOCATION), administrator);
        assertThat(updated.activity()).isEqualTo("Clean and disinfect");
        assertThat(updated.version()).isEqualTo(first.version() + 1);
        assertThat(updated.evidenceRequirements()).extracting(ObligationDefinitionService.EvidenceRequirementResponse::type)
                .containsExactly(EvidenceRequirementType.LOCATION);
        assertThat(definitions.findByIdAndServicePlanId(second.id(), planId).orElseThrow()
                .getEvidenceRequirements()).extracting(EvidenceRequirement::getEvidenceType)
                .containsExactly(EvidenceRequirementType.QR_CODE);
    }

    @Test
    void rejectsInvalidScheduleMetadataAndStaleVersion() {
        var invalid = new ObligationDefinitionService.DefinitionRequest(1L, "Activity", "Instructions", "Criteria",
                RecurrenceType.WEEKLY, null, 1, Set.of(), LocalTime.of(8, 0), 60,
                List.of(new ObligationDefinitionService.EvidenceRequirementRequest(EvidenceRequirementType.PHOTO,
                        true, 0, 1, Map.of("unknown", "value"))), null);
        assertThatThrownBy(() -> definitionService.create(planId, invalid, administrator))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(error -> assertThat(((FieldValidationException) error).getDetails())
                        .containsKeys("daysOfWeek", "evidenceRequirements[0].minimumCount",
                                "evidenceRequirements[0].constraints.unknown"));

        var created = definitionService.create(planId, weeklyRequest("Activity", null,
                EvidenceRequirementType.PHOTO), administrator);
        assertThatThrownBy(() -> definitionService.update(planId, created.id(),
                weeklyRequest("Changed", created.version() + 1, EvidenceRequirementType.PHOTO), administrator))
                .isInstanceOf(ApiConflictException.class)
                .satisfies(error -> assertThat(((ApiConflictException) error).getCode()).isEqualTo("VERSION_CONFLICT"));
    }

    @Test
    void activePlanRoleAndTenantBoundariesRejectMutation() {
        User supervisor = users.save(new User("Supervisor", "definition-supervisor@test.com",
                passwordEncoder.encode("Password123!"), Role.SUPERVISOR, 1L, 1L));
        assertThatThrownBy(() -> definitionService.create(planId,
                weeklyRequest("Activity", null, EvidenceRequirementType.PHOTO), supervisor))
                .isInstanceOf(AccessDeniedException.class);

        sites.save(new SiteScope(2L, 2L, "Foreign site"));
        User foreignAdmin = users.save(new User("Foreign admin", "foreign-definition@test.com",
                passwordEncoder.encode("Password123!"), Role.ADMIN, 2L, 2L));
        assertThatThrownBy(() -> definitionService.create(planId,
                weeklyRequest("Activity", null, EvidenceRequirementType.PHOTO), foreignAdmin))
                .isInstanceOf(ResourceNotFoundException.class);

        jdbc.update("UPDATE service_plans SET state = 'ACTIVE' WHERE id = ?", planId);
        assertThatThrownBy(() -> definitionService.create(planId,
                weeklyRequest("Activity", null, EvidenceRequirementType.PHOTO), administrator))
                .isInstanceOf(ApiConflictException.class)
                .satisfies(error -> assertThat(((ApiConflictException) error).getCode()).isEqualTo("PLAN_NOT_DRAFT"));
    }

    @Test
    void httpContractCreatesDefinitionAndDocumentsEndpoints() throws Exception {
        String body = """
                {"siteId":1,"activity":"Clean floors","instructions":"Use approved product",
                "acceptanceCriteria":"No visible residue","recurrenceType":"WEEKLY","recurrenceInterval":1,
                "daysOfWeek":["MONDAY"],"localStartTime":"08:00:00","windowDurationMinutes":120,
                "evidenceRequirements":[{"type":"PHOTO","required":true,"minimumCount":1,
                "maximumCount":2,"constraints":{"requireGeolocation":"true"}}]}
                """;
        mockMvc.perform(post("/api/v1/service-plans/" + planId + "/obligation-definitions")
                        .header("Authorization", "Bearer " + login(administrator.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").doesNotExist())
                .andExpect(jsonPath("$.timezone").value("America/Lima"))
                .andExpect(jsonPath("$.evidenceRequirements[0].type").value("PHOTO"));
        mockMvc.perform(get("/api-docs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("obligation-definitions")));
    }

    private ObligationDefinitionService.DefinitionRequest weeklyRequest(String activity, Long version,
                                                                         EvidenceRequirementType type) {
        Map<String, String> constraints = switch (type) {
            case PHOTO -> Map.of("requireGeolocation", "true");
            case QR_CODE -> Map.of("expectedPrefix", "OPERVIA-");
            case LOCATION -> Map.of("maxAccuracyMeters", "25");
            case SIGNATURE -> Map.of("signerLabel", "Supervisor");
        };
        return new ObligationDefinitionService.DefinitionRequest(1L, activity, "Instructions", "Criteria",
                RecurrenceType.WEEKLY, null, 1, Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                LocalTime.of(8, 0), 120,
                List.of(new ObligationDefinitionService.EvidenceRequirementRequest(type, true, 1, 2, constraints)),
                version);
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
        plans.deleteAll();
        companyServices.deleteAll();
    }
}
