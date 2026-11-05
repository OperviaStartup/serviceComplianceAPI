package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.SiteScope;
import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.SiteScopeRepository;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.serviceplanning.application.ServicePlanService;
import com.api.servicecompliance.serviceplanning.domain.model.CompanyService;
import com.api.servicecompliance.serviceplanning.domain.repository.CompanyServiceRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanIdempotencyRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanRepository;
import com.api.servicecompliance.shared.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ServicePlanIntegrationTests {
    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository users;
    @Autowired SiteScopeRepository sites;
    @Autowired CompanyServiceRepository companyServices;
    @Autowired ServicePlanRepository plans;
    @Autowired ServicePlanIdempotencyRepository idempotencyRecords;
    @Autowired ServicePlanService servicePlans;

    private User administrator;
    private CompanyService cleaning;

    @BeforeEach
    void setUp() {
        cleanServicePlanningData();
        sites.deleteAll();
        users.deleteAll();
        sites.save(new SiteScope(1L, 1L, "Legacy site"));
        administrator = users.save(new User("Plan Administrator", "plan-admin@test.com",
                passwordEncoder.encode("Password123!"), Role.ADMIN, 1L, 1L));
        cleaning = companyServices.save(new CompanyService(1L, "CLEANING", "Cleaning"));
    }

    @AfterEach
    void tearDown() {
        cleanServicePlanningData();
        users.deleteAll();
        sites.deleteAll();
    }

    @Test
    void administratorCreatesAndReadsCompanyDraftWithInclusiveLimaDates() throws Exception {
        String token = login(administrator.getEmail());
        String body = requestBody(cleaning.getId(), 1L, "2026-11-10", "2026-12-31");

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/service-plans")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "plan-create-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/service-plans/")))
                .andExpect(jsonPath("$.companyId").value(1))
                .andExpect(jsonPath("$.serviceId").value(cleaning.getId()))
                .andExpect(jsonPath("$.siteId").value(1))
                .andExpect(jsonPath("$.startDate").value("2026-11-10"))
                .andExpect(jsonPath("$.endDate").value("2026-12-31"))
                .andExpect(jsonPath("$.timezone").value("America/Lima"))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.missingActivationRequirements[0]")
                        .value("OBLIGATION_DEFINITION_REQUIRED"))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/service-plans/" + created.get("id").asLong())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.get("id").asLong()));
    }

    @Test
    void exactIdempotencyReplayReturnsOriginalPlanAndChangedPayloadConflicts() throws Exception {
        String token = login(administrator.getEmail());
        String original = requestBody(cleaning.getId(), 1L, "2026-11-10", "2026-12-31");
        String key = "plan-create-replay";
        long originalId = objectMapper.readTree(mockMvc.perform(post("/api/v1/service-plans")
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/v1/service-plans")
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(originalId));
        assertThat(plans.count()).isOne();

        mockMvc.perform(post("/api/v1/service-plans")
                        .header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(cleaning.getId(), 1L, "2026-11-10", "2027-01-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISMATCH"));
        assertThat(plans.count()).isOne();
    }

    @Test
    void concurrentExactRequestsCreateOnePlan() throws Exception {
        var request = new ServicePlanService.CreateServicePlanRequest(cleaning.getId(), 1L,
                LocalDate.parse("2026-11-10"), LocalDate.parse("2026-12-31"));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> createConcurrently(request, ready, start));
            var second = executor.submit(() -> createConcurrently(request, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .allMatch(id -> id != null)
                    .containsOnly(first.get());
            assertThat(plans.count()).isOne();
            assertThat(idempotencyRecords.count()).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void invalidPeriodAndMissingIdempotencyKeyReturnFieldDetails() throws Exception {
        String token = login(administrator.getEmail());
        String invalidPeriod = requestBody(cleaning.getId(), 1L, "2026-12-31", "2026-11-10");
        mockMvc.perform(post("/api/v1/service-plans")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "plan-invalid-period")
                        .contentType(MediaType.APPLICATION_JSON).content(invalidPeriod))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.endDate[0]").exists());

        mockMvc.perform(post("/api/v1/service-plans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(cleaning.getId(), 1L, "2026-11-10", "2026-12-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.idempotencyKey[0]").exists());
    }

    @Test
    void roleAndTenantBoundariesProtectCreateAndRead() throws Exception {
        User supervisor = users.save(new User("Supervisor", "plan-supervisor@test.com",
                passwordEncoder.encode("Password123!"), Role.SUPERVISOR, 1L, 1L));
        sites.save(new SiteScope(2L, 2L, "Foreign site"));
        CompanyService foreignService = companyServices.save(new CompanyService(2L, "CLEANING", "Cleaning"));
        User foreignAdministrator = users.save(new User("Foreign Administrator", "foreign-plan-admin@test.com",
                passwordEncoder.encode("Password123!"), Role.ADMIN, 2L, 2L));
        String body = requestBody(cleaning.getId(), 1L, "2026-11-10", "2026-12-31");

        mockMvc.perform(post("/api/v1/service-plans")
                        .header("Authorization", "Bearer " + login(supervisor.getEmail()))
                        .header("Idempotency-Key", "supervisor-denied")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/service-plans")
                        .header("Authorization", "Bearer " + login(administrator.getEmail()))
                        .header("Idempotency-Key", "foreign-scope-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(foreignService.getId(), 2L, "2026-11-10", "2026-12-31")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.details.siteId[0]").exists())
                .andExpect(jsonPath("$.details.serviceId[0]").exists());

        long foreignPlanId = objectMapper.readTree(mockMvc.perform(post("/api/v1/service-plans")
                        .header("Authorization", "Bearer " + login(foreignAdministrator.getEmail()))
                        .header("Idempotency-Key", "foreign-plan-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(foreignService.getId(), 2L, "2026-11-10", "2026-12-31")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        mockMvc.perform(get("/api/v1/service-plans/" + foreignPlanId)
                        .header("Authorization", "Bearer " + login(administrator.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiDocumentsServicePlanEndpointsAndIdempotencyHeader() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/service-plans")))
                .andExpect(content().string(containsString("Idempotency-Key")))
                .andExpect(content().string(containsString("America/Lima")));
    }

    private Long createConcurrently(ServicePlanService.CreateServicePlanRequest request,
                                    CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }
        return servicePlans.create(request, "concurrent-plan-key", administrator).id();
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private static String requestBody(Long serviceId, Long siteId, String startDate, String endDate) {
        return "{\"serviceId\":" + serviceId + ",\"siteId\":" + siteId
                + ",\"startDate\":\"" + startDate + "\",\"endDate\":\"" + endDate + "\"}";
    }

    private void cleanServicePlanningData() {
        idempotencyRecords.deleteAll();
        plans.deleteAll();
        companyServices.deleteAll();
    }
}
