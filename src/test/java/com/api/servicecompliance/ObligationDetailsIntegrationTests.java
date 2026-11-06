package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
class ObligationDetailsIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired ObligationRepository obligations;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final List<User> fixtureUsers = new ArrayList<>();
    private User supervisor;
    private User administrator;
    private User owner;
    private User otherOperator;
    private User foreignSupervisor;
    private User otherSiteSupervisor;

    @BeforeEach
    void setUp() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            if (connection.getMetaData().getDatabaseProductName().contains("PostgreSQL")) {
                jdbc.update("INSERT INTO companies (id, name) VALUES (2, 'Test company') ON CONFLICT (id) DO NOTHING");
                jdbc.update("INSERT INTO sites (id, company_id, name) VALUES (2, 2, 'Foreign site') ON CONFLICT (id) DO NOTHING");
                jdbc.update("INSERT INTO sites (id, company_id, name) VALUES (3, 1, 'Other site') ON CONFLICT (id) DO NOTHING");
            }
        }
        supervisor = user(Role.SUPERVISOR, 1L, 1L);
        administrator = user(Role.ADMIN, 1L, 1L);
        owner = user(Role.OPERATOR, 1L, 1L);
        otherOperator = user(Role.OPERATOR, 1L, 1L);
        foreignSupervisor = user(Role.SUPERVISOR, 2L, 2L);
        otherSiteSupervisor = user(Role.SUPERVISOR, 1L, 3L);
    }

    @AfterEach
    void cleanFixtures() {
        for (User user : fixtureUsers) {
            obligations.deleteAll(obligations.findByAssignedOperatorId(user.getId()));
            var families = jdbc.queryForList("SELECT DISTINCT family_id FROM refresh_sessions WHERE user_id = ?",
                    String.class, user.getId());
            jdbc.update("DELETE FROM refresh_sessions WHERE user_id = ?", user.getId());
            families.forEach(id -> jdbc.update("DELETE FROM refresh_families WHERE id = ?", id));
        }
        users.deleteAll(fixtureUsers);
        fixtureUsers.clear();
    }

    @Test
    void createsAndReloadsOrderedStepsAndDistinctPhotoRequirements() throws Exception {
        JsonNode created = create(structuredRequest(), supervisor);
        assertThat(created.path("steps").size()).isEqualTo(3);
        assertThat(created.path("photoRequirements").size()).isEqualTo(2);
        assertThat(created.path("steps").get(0).path("id").asLong()).isPositive();
        assertThat(created.path("photoRequirements").get(0).path("id").asLong()).isPositive();
        assertThat(created.path("photoRequirements").get(0).path("id"))
                .isNotEqualTo(created.path("photoRequirements").get(1).path("id"));

        mockMvc.perform(get("/api/v1/obligations/" + created.path("id").asLong())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("America/Lima"))
                .andExpect(jsonPath("$.startsAt").value("2030-01-01T15:00:00Z"))
                .andExpect(jsonPath("$.dueAt").value("2030-01-01T15:30:00Z"))
                .andExpect(jsonPath("$.steps", hasSize(3)))
                .andExpect(jsonPath("$.steps[0].title").value("Piso"))
                .andExpect(jsonPath("$.steps[0].position").value(1))
                .andExpect(jsonPath("$.steps[2].title").value("Área de espera"))
                .andExpect(jsonPath("$.steps[2].position").value(3))
                .andExpect(jsonPath("$.photoRequirements[0].title").value("Piso terminado"))
                .andExpect(jsonPath("$.photoRequirements[1].title").value("Mostrador limpio"))
                .andExpect(jsonPath("$.photoRequirements[1].position").value(2))
                .andExpect(jsonPath("$.photoRequirements[0].id")
                        .value(created.path("photoRequirements").get(0).path("id").asLong()));
    }

    @Test
    void keepsExistingListShapeAndAssignedUserFilter() throws Exception {
        JsonNode created = create(structuredRequest(), supervisor);
        mockMvc.perform(get("/api/v1/obligations").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(created.path("id").asLong()))
                .andExpect(jsonPath("$[0].assignedOperatorId").value(owner.getId()))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$[0].steps").doesNotExist())
                .andExpect(jsonPath("$[0].photoRequirements").doesNotExist());
        mockMvc.perform(get("/api/v1/obligations").header("Authorization", bearer(otherOperator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void preservesLegacyCreationWithoutFabricatingExecutionDetails() throws Exception {
        var request = structuredRequest();
        request.remove(List.of("startsAt", "steps", "photoRequirements"));
        JsonNode created = create(request, supervisor);
        mockMvc.perform(get("/api/v1/obligations/" + created.path("id").asLong())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").doesNotExist())
                .andExpect(jsonPath("$.steps", hasSize(0)))
                .andExpect(jsonPath("$.photoRequirements", hasSize(0)));
    }

    @Test
    void supportsAnExplicitObligationWithoutRequiredPhotos() throws Exception {
        var request = structuredRequest();
        request.putArray("photoRequirements");
        JsonNode created = create(request, administrator);
        assertThat(created.path("photoRequirements").size()).isZero();
        assertThat(created.path("steps").size()).isEqualTo(3);
    }

    @Test
    void protectsDetailByCompanySiteAndOwnership() throws Exception {
        long id = create(structuredRequest(), supervisor).path("id").asLong();
        mockMvc.perform(get("/api/v1/obligations/" + id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/obligations/" + id).header("Authorization", bearer(otherOperator)))
                .andExpect(status().isForbidden());
        for (User actor : List.of(foreignSupervisor, otherSiteSupervisor)) {
            mockMvc.perform(get("/api/v1/obligations/" + id).header("Authorization", bearer(actor)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        for (User actor : List.of(supervisor, administrator)) {
            mockMvc.perform(get("/api/v1/obligations/" + id).header("Authorization", bearer(actor)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/obligations/9223372036854775807").header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsIncompleteConfigurationWithoutSavingAnObligation() throws Exception {
        long before = obligations.count();
        var request = structuredRequest();
        request.remove("startsAt");
        reject(request, "startsAt");
        request = structuredRequest();
        request.putArray("steps");
        reject(request, "steps");
        request = structuredRequest();
        request.remove("photoRequirements");
        reject(request, "photoRequirements");
        assertThat(obligations.count()).isEqualTo(before);
    }

    @Test
    void rejectsEqualAndReversedExecutionWindows() throws Exception {
        for (String dueAt : List.of("2030-01-01T15:00:00Z", "2030-01-01T14:59:59Z")) {
            var request = structuredRequest();
            request.put("dueAt", dueAt);
            reject(request, "dueAt");
        }
    }

    @Test
    void rejectsInvalidAndNullNestedItems() throws Exception {
        var request = structuredRequest();
        ((com.fasterxml.jackson.databind.node.ObjectNode) request.path("steps").get(0)).put("title", " ");
        reject(request, "steps[0].title");
        request = structuredRequest();
        request.putArray("steps").addNull();
        reject(request, "steps[0]");
        request = structuredRequest();
        request.putArray("photoRequirements").addNull();
        reject(request, "photoRequirements[0]");
        request = structuredRequest();
        ((com.fasterxml.jackson.databind.node.ObjectNode) request.path("photoRequirements").get(0))
                .put("description", "a".repeat(2001));
        reject(request, "photoRequirements[0].description");
    }

    @Test
    void preservesCreationPermissionsAndRejectsIneligibleAssignees() throws Exception {
        mockMvc.perform(post("/api/v1/obligations").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(structuredRequest().toString()))
                .andExpect(status().isForbidden());
        User foreignOperator = user(Role.OPERATOR, 2L, 2L);
        User otherSiteOperator = user(Role.OPERATOR, 1L, 3L);
        for (User ineligible : List.of(supervisor, foreignOperator, otherSiteOperator)) {
            var request = structuredRequest();
            request.put("assignedOperatorId", ineligible.getId());
            mockMvc.perform(post("/api/v1/obligations").header("Authorization", bearer(supervisor))
                            .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                    .andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE users SET active = false WHERE id = ?", owner.getId());
        mockMvc.perform(post("/api/v1/obligations").header("Authorization", bearer(supervisor))
                        .contentType(MediaType.APPLICATION_JSON).content(structuredRequest().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void documentsTypedDetailAndCreationContractsInOpenApi() throws Exception {
        JsonNode api = json.readTree(mockMvc.perform(get("/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(api.path("paths").has("/api/v1/obligations/{id}")).isTrue();
        JsonNode schema = api.path("components").path("schemas").path("ObligationDetail").path("properties");
        assertThat(schema.has("steps")).isTrue();
        assertThat(schema.has("photoRequirements")).isTrue();
        assertThat(schema.has("startsAt")).isTrue();
    }

    private User user(Role role, Long companyId, Long siteId) {
        User user = users.save(new User("Fixture " + role, UUID.randomUUID() + "@example.test",
                passwords.encode("TestPassword123!"), role, companyId, siteId));
        fixtureUsers.add(user);
        return user;
    }

    private String bearer(User user) throws Exception {
        var request = json.createObjectNode().put("identifier", user.getEmail()).put("password", "TestPassword123!");
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).path("token").asText();
    }

    private JsonNode create(com.fasterxml.jackson.databind.node.ObjectNode request, User actor) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/v1/obligations").header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void reject(com.fasterxml.jackson.databind.node.ObjectNode request, String field) throws Exception {
        String response = mockMvc.perform(post("/api/v1/obligations").header("Authorization", bearer(supervisor))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(response).path("details").has(field)).isTrue();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode structuredRequest() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree("""
                {"title":"Limpiar recepción","description":"Instrucciones de limpieza",
                 "siteName":"Recepción","startsAt":"2030-01-01T15:00:00Z","dueAt":"2030-01-01T15:30:00Z",
                 "steps":[{"title":"Piso","instructions":"Limpiar y secar"},
                          {"title":"Mostrador"},{"title":"Área de espera"}],
                 "photoRequirements":[{"title":"Piso terminado","description":"Mostrar el piso seco"},
                                      {"title":"Mostrador limpio"}]}
                """);
        request.put("assignedOperatorId", owner.getId());
        return request;
    }
}
