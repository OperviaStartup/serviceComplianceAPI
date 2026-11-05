package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.application.RefreshService;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshSessionRepository;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshFamilyRepository;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.model.ExecutionResult;
import com.api.servicecompliance.executions.domain.repository.EvidenceRepository;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class ServiceComplianceIntegrationTests {
    @Autowired MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();
    @Autowired UserRepository userRepository;
    @Autowired ObligationRepository obligationRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RefreshService refreshService;
    @Autowired RefreshSessionRepository sessions;
    @Autowired RefreshFamilyRepository families;
    @Autowired ExecutionRepository executions;
    @Autowired EvidenceRepository evidence;

    @BeforeEach void cleanDatabase(){evidence.deleteAll(); executions.deleteAll(); obligationRepository.deleteAll(); sessions.deleteAll(); families.deleteAll(); userRepository.deleteAll();}

    private void ensureAdditionalPostgresScopes() throws java.sql.SQLException {
        try (var connection=java.util.Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().contains("PostgreSQL")) return;
        }
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (2, 'Other company') ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO sites (id, company_id, name) VALUES (2, 2, 'Other company site') ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO sites (id, company_id, name) VALUES (3, 1, 'Other legacy site') ON CONFLICT (id) DO NOTHING");
    }

    @Test void shouldDenyPublicRegistrationWithoutCreatingAccount() throws Exception {
        String register = "{\"fullName\":\"Operator\",\"email\":\"operator@test.com\",\"password\":\"Password123!\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        org.assertj.core.api.Assertions.assertThat(userRepository.count()).isZero();
    }

    @Test void shouldDocumentAuthenticationSessionEndpoints() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("\"/api/v1/auth/refresh\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("\"/api/v1/auth/logout\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("Registro público deshabilitado")));
    }
    @Test void shouldLoginAndReadAuthenticatedProfile() throws Exception {
        User user=userRepository.save(new User("Operator", "operator@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String loginBody="{\"email\":\"operator@test.com\",\"password\":\"Password123!\"}";
        JsonNode response=objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String[] tokenParts=response.get("token").asText().split("\\.");
        JsonNode claims=objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(tokenParts[1]));
        org.assertj.core.api.Assertions.assertThat(claims.get("userId").asLong()).isEqualTo(user.getId());
        org.assertj.core.api.Assertions.assertThat(claims.get("companyId").asLong()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(claims.get("siteId").asLong()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(claims.get("role").asText()).isEqualTo("OPERATOR");
        org.assertj.core.api.Assertions.assertThat(claims.get("exp").asLong()-claims.get("iat").asLong()).isEqualTo(1800L);
        org.assertj.core.api.Assertions.assertThat(response.get("refreshToken").asText()).isNotBlank();
        long refreshLifetimeSeconds=java.time.Duration.between(Instant.now(),sessions.findAll().get(0).getExpiresAt()).getSeconds();
        org.assertj.core.api.Assertions.assertThat(refreshLifetimeSeconds).isBetween(604790L,604800L);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+response.get("token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("operator@test.com"));
    }

    @Test void shouldReturnUnauthorizedErrorForInvalidLogin() throws Exception {
        String invalid = "{\"email\":\"missing@test.com\",\"password\":\"Password123!\"}";
        JsonNode missing=objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED")).andReturn().getResponse().getContentAsString());
        userRepository.save(new User("Operator", "known@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String wrong = "{\"email\":\"known@test.com\",\"password\":\"WrongPassword123!\"}";
        JsonNode mismatch=objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(mismatch.get("code")).isEqualTo(missing.get("code"));
        org.assertj.core.api.Assertions.assertThat(mismatch.get("message")).isEqualTo(missing.get("message"));
    }

    @Test void shouldReturnUnauthorizedErrorForProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/obligations"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test void shouldRejectAccessTokenWhenAccountNoLongerExists() throws Exception {
        User operator = userRepository.save(new User("Former Operator", "former@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String token = login(operator.getEmail());
        sessions.deleteAll();
        families.deleteAll();
        userRepository.delete(operator);

        mockMvc.perform(get("/api/v1/obligations").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test void shouldNeverEchoMalformedAccessToken() throws Exception {
        String token = "private-token-value-that-must-not-appear";
        String body = mockMvc.perform(get("/api/v1/obligations").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(token);
    }

    @Test void shouldUseCurrentRoleInsteadOfRoleInPreviouslyIssuedToken() throws Exception {
        User supervisor = userRepository.save(new User("Former Supervisor", "former-supervisor@test.com", passwordEncoder.encode("Password123!"), Role.SUPERVISOR, 1L, 1L));
        User operator = userRepository.save(new User("Assignee", "assignee@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String token = login(supervisor.getEmail());
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", "OPERATOR", supervisor.getId());
        String request = "{\"title\":\"Clean warehouse\",\"description\":\"Clean floor\",\"siteName\":\"Warehouse\",\"assignedOperatorId\":" + operator.getId() + ",\"dueAt\":\"2030-01-01T10:00:00Z\"}";

        mockMvc.perform(post("/api/v1/obligations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
    }

    @Test void shouldRejectDisabledAccountLoginAndPreviouslyIssuedAccessToken() throws Exception {
        User operator = userRepository.save(new User("Disabled Operator", "disabled@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String token = login(operator.getEmail());
        jdbcTemplate.update("UPDATE users SET active = ? WHERE id = ?", false, operator.getId());

        String request = "{\"email\":\"disabled@test.com\",\"password\":\"Password123!\"}";
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/obligations").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test void shouldRegisterExecutionOnlyForAssignedOperator() throws Exception {
        User assigned = userRepository.save(new User("Assigned", "assigned@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        User another = userRepository.save(new User("Another", "another@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        Obligation obligation = obligationRepository.save(new Obligation("Clean lobby", "Clean the main lobby", "Main site", assigned.getId(), Instant.now().plusSeconds(3600), 1L, 1L));
        String token = login("another@test.com");
        String body = "{\"obligationId\":"+obligation.getId()+",\"result\":\"COMPLETED\",\"notes\":\"Done\"}";
        mockMvc.perform(post("/api/v1/executions").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void shouldCreateExecutionForAssignedOperator() throws Exception {
        User assigned = userRepository.save(new User("Assigned", "assigned2@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        Obligation obligation = obligationRepository.save(new Obligation("Clean office", "Clean office area", "Office site", assigned.getId(), Instant.now().plusSeconds(3600), 1L, 1L));
        String token = login("assigned2@test.com");
        String body = "{\"obligationId\":"+obligation.getId()+",\"result\":\"COMPLETED\",\"notes\":\"Completed\"}";
        mockMvc.perform(post("/api/v1/executions").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.obligationId").value(obligation.getId()));
    }

    @Test void shouldAllowSupervisorToCreateObligationAndRejectOperator() throws Exception {
        User supervisor = userRepository.save(new User("Supervisor", "supervisor@test.com", passwordEncoder.encode("Password123!"), Role.SUPERVISOR, 1L, 1L));
        User operator = userRepository.save(new User("Operator", "operator2@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String request = "{\"title\":\"Clean warehouse\",\"description\":\"Clean warehouse floor\",\"siteName\":\"Warehouse\",\"assignedOperatorId\":"+operator.getId()+",\"dueAt\":\"2030-01-01T10:00:00Z\"}";
        mockMvc.perform(post("/api/v1/obligations").header("Authorization","Bearer "+login("operator2@test.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/obligations").header("Authorization","Bearer "+login("supervisor@test.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.assignedOperatorId").value(operator.getId()));
        mockMvc.perform(get("/api/v1/obligations").header("Authorization","Bearer "+login("operator2@test.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Clean warehouse"));
    }

    @Test void shouldAllowAdministratorWithinScopeAndRejectCrossCompanyAssignee() throws Exception {
        ensureAdditionalPostgresScopes();
        User administrator=userRepository.save(new User("Administrator", "admin@test.com", passwordEncoder.encode("Password123!"), Role.ADMIN, 1L, 1L));
        User localOperator=userRepository.save(new User("Local", "local@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        User foreignOperator=userRepository.save(new User("Foreign", "foreign@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 2L, 2L));
        String localRequest="{\"title\":\"Local work\",\"description\":\"Local description\",\"siteName\":\"Legacy site\",\"assignedOperatorId\":"+localOperator.getId()+",\"dueAt\":\"2030-01-01T10:00:00Z\"}";
        String foreignRequest="{\"title\":\"Foreign work\",\"description\":\"Foreign description\",\"siteName\":\"Other site\",\"assignedOperatorId\":"+foreignOperator.getId()+",\"dueAt\":\"2030-01-01T10:00:00Z\"}";
        String token=login(administrator.getEmail());
        mockMvc.perform(post("/api/v1/obligations").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(localRequest))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/obligations").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(foreignRequest))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void refreshRotationReplayRevokesFamily() throws Exception {
        userRepository.save(new User("Operator", "rotation@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String body = "{\"email\":\"rotation@test.com\",\"password\":\"Password123!\"}";
        String first = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("refreshToken").asText();
        String rotatedBody = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(first))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = objectMapper.readTree(rotatedBody).get("refreshToken").asText();
        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first);
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(first))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(second))).andExpect(status().isUnauthorized());
    }

    @Test void concurrentRefreshOfSameTokenRevokesWinningFamily() throws Exception {
        User user = userRepository.save(new User("Operator", "concurrent@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String token = refreshService.start(user);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<RefreshService.Rotation> refresh = () -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return refreshService.rotate(token);
            };
            var first = executor.submit(refresh);
            var second = executor.submit(refresh);
            org.assertj.core.api.Assertions.assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var rotations = java.util.Arrays.asList(first.get(20, java.util.concurrent.TimeUnit.SECONDS), second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            org.assertj.core.api.Assertions.assertThat(rotations.stream().filter(java.util.Objects::nonNull)).hasSize(1);
            String winningToken = rotations.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow().token();
            org.assertj.core.api.Assertions.assertThat(refreshService.rotate(winningToken)).isNull();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test void suspensionInvalidatesAllSessionsEvenAfterReactivation() throws Exception {
        User user=userRepository.save(new User("Operator", "suspend@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String body="{\"email\":\"suspend@test.com\",\"password\":\"Password123!\"}";
        JsonNode login=objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        refreshService.suspend(user.getId());
        jdbcTemplate.update("UPDATE users SET active = true WHERE id = ?", user.getId());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer "+login.get("token").asText())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(login.get("refreshToken").asText()))).andExpect(status().isUnauthorized());
    }
    @Test void executionReadsEnforceCompanySiteAndOwnership() throws Exception {
        ensureAdditionalPostgresScopes();
        User owner=userRepository.save(new User("Owner", "owner@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        User other=userRepository.save(new User("Other", "other@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        User company=userRepository.save(new User("Other company", "company@test.com", passwordEncoder.encode("Password123!"), Role.SUPERVISOR, 2L, 2L));
        User site=userRepository.save(new User("Other site", "site@test.com", passwordEncoder.encode("Password123!"), Role.SUPERVISOR, 1L, 3L));
        User supervisor=userRepository.save(new User("Supervisor", "reader@test.com", passwordEncoder.encode("Password123!"), Role.SUPERVISOR, 1L, 1L));
        Obligation obligation=obligationRepository.save(new Obligation("Title", "Description", "Site", owner.getId(), Instant.now(), 1L, 1L));
        Execution execution=executions.save(new Execution(obligation.getId(), owner.getId(), ExecutionResult.COMPLETED, "Done"));
        for (User denied : java.util.List.of(other,company,site)) {
            String token=login(denied.getEmail());
            mockMvc.perform(get("/api/v1/executions/"+execution.getId()).header("Authorization", "Bearer "+token)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/executions/"+execution.getId()+"/evidence").header("Authorization", "Bearer "+token)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/executions/"+execution.getId()).header("Authorization", "Bearer "+login(supervisor.getEmail()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/executions/"+execution.getId()).header("Authorization", "Bearer "+login(owner.getEmail()))).andExpect(status().isOk());
    }

    @Test void logoutRevokesRefreshFamily() throws Exception {
        userRepository.save(new User("Operator", "logout@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, 1L, 1L));
        String body="{\"email\":\"logout@test.com\",\"password\":\"Password123!\"}";
        JsonNode login=objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String refresh=login.get("refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refresh))).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refresh))).andExpect(status().isUnauthorized());
    }

    private String refreshBody(String token) { return "{\"refreshToken\":\""+token+"\"}"; }

    private String login(String email) throws Exception {
        String body = "{\"email\":\""+email+"\",\"password\":\"Password123!\"}";
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
