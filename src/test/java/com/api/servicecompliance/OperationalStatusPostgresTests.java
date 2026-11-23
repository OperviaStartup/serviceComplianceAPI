package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.model.ExceptionReason;
import com.api.servicecompliance.executions.domain.model.OperationalException;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.executions.domain.repository.OperationalExceptionRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.serviceplanning.domain.model.ObligationDefinition;
import com.api.servicecompliance.serviceplanning.domain.model.RecurrenceType;
import com.api.servicecompliance.serviceplanning.domain.model.ServicePlan;
import com.api.servicecompliance.serviceplanning.domain.repository.ObligationDefinitionRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanRepository;
import com.api.servicecompliance.shared.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "FIELD_OPERATIONS_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/field_operations_test")
class OperationalStatusPostgresTests {
    private static final String PATH = "/api/v1/operational-status";
    private static final String SCHEMA = "reporting_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant NOW = Instant.parse("2030-01-02T06:00:00Z");
    private static final AtomicReference<Instant> SERVER_TIME = new AtomicReference<>(NOW);
    private static final LocalDate DAY = LocalDate.of(2030, 1, 2);
    private static final String PASSWORD = "OperationalStatusTest123!";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObligationRepository obligations;
    @Autowired ExecutionRepository executions;
    @Autowired OperationalExceptionRepository exceptions;
    @Autowired ServicePlanRepository plans;
    @Autowired ObligationDefinitionRepository definitions;

    private User supervisor;
    private User operator;
    private ServicePlan plan;
    private String supervisorToken;

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        Clock reportingTestClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() { return ZoneOffset.UTC; }

                @Override
                public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }

                @Override
                public Instant instant() { return SERVER_TIME.get(); }
            };
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> url() + "?currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", OperationalStatusPostgresTests::username);
        properties.add("spring.datasource.password", OperationalStatusPostgresTests::password);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void fixtures() throws Exception {
        SERVER_TIME.set(NOW);
        jdbc.update("INSERT INTO companies (id, name) VALUES (2, 'Other company')");
        jdbc.update("INSERT INTO sites (id, company_id, name) VALUES (2, 1, 'Other site'), (3, 2, 'Different tenant')");
        supervisor = user(Role.SUPERVISOR, 1, 1);
        operator = user(Role.OPERATOR, 1, 1);
        plan = plans.saveAndFlush(new ServicePlan(1L, 1L, 1L, DAY.minusDays(1), DAY.plusDays(1),
                supervisor.getId(), NOW));
        supervisorToken = login(supervisor);
    }

    @Test
    void derivesAllStatusesAndAppliesOneSetOfFiltersToRowsAndTotals() throws Exception {
        Instant start = NOW.minusSeconds(3600);
        Obligation assigned = generated(operator, start, NOW.plusSeconds(1), plan);
        Obligation overdue = generated(operator, start, NOW, plan);
        Obligation inProgress = generated(operator, start, NOW.minusSeconds(1), plan);
        execution(inProgress, false, false);
        Obligation submitted = generated(operator, start, NOW.minusSeconds(1), plan);
        execution(submitted, true, false);
        Obligation excepted = generated(operator, start, NOW.minusSeconds(1), plan);
        execution(excepted, true, true);
        generated(user(Role.OPERATOR, 1, 1), start, NOW, plan);
        Obligation previousDay = generated(operator, start.minusSeconds(1), NOW, plan);
        Obligation nextDay = generated(operator, start.plusSeconds(86400), start.plusSeconds(90000), plan);
        direct(user(Role.OPERATOR, 1, 2), start);
        direct(user(Role.OPERATOR, 2, 3), start);
        obligations.saveAndFlush(new Obligation("Unassigned", "Instructions", "Site", null,
                NOW, 1L, 1L));
        jdbc.update("INSERT INTO company_services (id, company_id, code, name) VALUES (2, 1, 'OTHER', 'Other')");
        ServicePlan otherService = plans.saveAndFlush(new ServicePlan(1L, 2L, 1L, DAY, DAY,
                supervisor.getId(), NOW));
        generated(operator, start, NOW, otherService);
        direct(operator, start);

        mvc.perform(filtered()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].id").value(assigned.getId()))
                .andExpect(jsonPath("$.items[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$.items[1].id").value(overdue.getId()))
                .andExpect(jsonPath("$.items[1].status").value("OVERDUE"))
                .andExpect(jsonPath("$.items[2].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[3].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.items[4].status").value("EXCEPTED"))
                .andExpect(jsonPath("$.items[0].serviceId").value(1))
                .andExpect(jsonPath("$.items[0].sourcePlanVersion").value(1))
                .andExpect(jsonPath("$.items[0].timezone").value("America/Lima"))
                .andExpect(jsonPath("$.counts.ASSIGNED").value(1))
                .andExpect(jsonPath("$.counts.OVERDUE").value(1))
                .andExpect(jsonPath("$.counts.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.counts.SUBMITTED").value(1))
                .andExpect(jsonPath("$.counts.EXCEPTED").value(1))
                .andExpect(jsonPath("$.total").value(5));
        mvc.perform(filtered().param("status", "EXCEPTED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(excepted.getId()))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.counts.SUBMITTED").value(0));
        assertThat(previousDay.getOccurrenceDate()).isEqualTo(DAY.minusDays(1));
        assertThat(nextDay.getOccurrenceDate()).isEqualTo(DAY.plusDays(1));
    }

    @Test
    void paginatesAtEqualStartTimesThroughInsertionsAndRejectsIncompatibleCursors() throws Exception {
        Instant start = NOW.minusSeconds(3600);
        Instant due = NOW.plusSeconds(1);
        Obligation first = generated(operator, start, due, plan);
        Obligation second = generated(operator, start, due, plan);
        String body = mvc.perform(filtered().param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(first.getId()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JSON.readTree(body).get("nextCursor").asText();

        generated(operator, start.minusSeconds(1), due, plan);
        Obligation third = generated(operator, start, due, plan);
        SERVER_TIME.set(due);
        mvc.perform(filtered().param("cursor", cursor).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.items[1].id").value(third.getId()))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.counts.ASSIGNED").value(3))
                .andExpect(jsonPath("$.asOf").value(JSON.readTree(body).get("asOf").asText()))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(filtered()).andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.OVERDUE").value(3));
        mvc.perform(filtered().param("cursor", cursor).param("status", "OVERDUE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.cursor").isArray());
        mvc.perform(filtered().param("cursor", cursor + ".invalid")).andExpect(status().isBadRequest());

        String anotherSupervisor = login(user(Role.SUPERVISOR, 1, 1));
        mvc.perform(filtered(anotherSupervisor).param("cursor", cursor))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesOtherRolesAndNeverIncludesOtherSitesOrTenantsInTotals() throws Exception {
        User otherSite = user(Role.OPERATOR, 1, 2);
        User otherCompany = user(Role.OPERATOR, 2, 3);
        direct(operator, NOW);
        direct(otherSite, NOW);
        direct(otherCompany, NOW);
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(request(login(operator))).andExpect(status().isForbidden());
        mvc.perform(request(login(user(Role.ADMIN, 1, 1)))).andExpect(status().isForbidden());
        mvc.perform(request(supervisorToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.counts.ASSIGNED").value(1))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(request(supervisorToken).param("siteId", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(request(supervisorToken).param("operatorId", otherCompany.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.counts.ASSIGNED").value(0));
        mvc.perform(request(supervisorToken).param("siteId", "0")).andExpect(status().isBadRequest());
        mvc.perform(request(supervisorToken).param("limit", "101")).andExpect(status().isBadRequest());
        mvc.perform(request(supervisorToken).param("fromDate", "2030-01-03").param("toDate", DAY.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/operational-status'].get.responses['200']").exists());
    }

    @Test
    void migratesAnExistingV20DatabaseWithLegacyRows() throws Exception {
        String schema = SCHEMA + "_upgrade";
        Flyway.configure().dataSource(url(), username(), password()).schemas(schema).target("20").load().migrate();
        try (var connection = DriverManager.getConnection(url() + "?currentSchema=" + schema, username(), password());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, full_name, email, password_hash, role, created_at, company_id, site_id)
                    VALUES (1, 'Operator', 'legacy@status.test', 'hash', 'OPERATOR', CURRENT_TIMESTAMP, 1, 1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO obligations (title, description, site_name, assigned_operator_id, status,
                                             due_at, company_id, site_id)
                    VALUES ('Legacy obligation', 'Instructions', 'Site', 1, 'ASSIGNED', CURRENT_TIMESTAMP, 1, 1)
                    """);
            var flyway = Flyway.configure().dataSource(url(), username(), password()).schemas(schema).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            try (var rows = statement.executeQuery("""
                    SELECT title, service_id, execution_state FROM operational_status_facts_v1
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("title")).isEqualTo("Legacy obligation");
                assertThat(rows.getObject("service_id")).isNull();
                assertThat(rows.getObject("execution_state")).isNull();
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private MockHttpServletRequestBuilder filtered() {
        return filtered(supervisorToken);
    }

    private MockHttpServletRequestBuilder filtered(String token) {
        return request(token).param("serviceId", "1").param("siteId", "1")
                .param("operatorId", operator.getId().toString())
                .param("fromDate", DAY.toString()).param("toDate", DAY.toString());
    }

    private MockHttpServletRequestBuilder request(String token) {
        return get(PATH).header("Authorization", "Bearer " + token);
    }

    private User user(Role role, long company, long site) {
        return users.saveAndFlush(new User(role.name(), UUID.randomUUID() + "@status.test",
                passwords.encode(PASSWORD), role, company, site));
    }

    private String login(User user) throws Exception {
        String body = "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}";
        String result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(result).get("token").asText();
    }

    private Obligation generated(User owner, Instant starts, Instant due, ServicePlan source) {
        LocalDate occurrence = LocalDate.ofInstant(starts, java.time.ZoneId.of(Obligation.TIMEZONE));
        var definition = definitions.saveAndFlush(new ObligationDefinition(source.getId(), 1L,
                "Clean lobby", "Instructions", "Criteria", RecurrenceType.ONCE, occurrence, 1,
                Set.of(), LocalTime.NOON, 60, List.of(), supervisor.getId(), NOW));
        var obligation = new Obligation("Clean lobby", "Instructions", "Site", starts, due, 1L, 1L,
                source.getId(), source.getBusinessVersion(), definition.getId(), definition.getVersion(),
                occurrence, List.of(), List.of());
        obligation.assignTo(owner.getId());
        return obligations.saveAndFlush(obligation);
    }

    private void direct(User owner, Instant when) {
        obligations.saveAndFlush(new Obligation("Manual", "Instructions", "Site", owner.getId(), when,
                owner.getCompanyId(), owner.getSiteId()));
    }

    private void execution(Obligation obligation, boolean submitted, boolean excepted) {
        Instant time = obligation.getStartsAt();
        var execution = Execution.start(obligation.getId(), operator.getId(), time);
        if (excepted) execution.exceptionReported(time);
        if (submitted) execution.submit(null, time);
        executions.saveAndFlush(execution);
        if (excepted) exceptions.saveAndFlush(new OperationalException(execution.getId(), null,
                ExceptionReason.OTHER, "Test exception", operator.getId(), time, UUID.randomUUID()));
    }

    private static String url() {
        return System.getenv("FIELD_OPERATIONS_TEST_JDBC_URL");
    }

    private static String username() {
        return System.getenv("FIELD_OPERATIONS_TEST_DB_USER");
    }

    private static String password() {
        return System.getenv().getOrDefault("FIELD_OPERATIONS_TEST_DB_PASSWORD", "");
    }
}
