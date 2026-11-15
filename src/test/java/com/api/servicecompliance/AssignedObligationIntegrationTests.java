package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshFamilyRepository;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshSessionRepository;
import com.api.servicecompliance.obligations.application.AssignedObligationQueryService;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.model.ObligationAssignmentChange;
import com.api.servicecompliance.obligations.domain.model.ObligationAssignmentChangeType;
import com.api.servicecompliance.obligations.domain.model.ObligationStatus;
import com.api.servicecompliance.obligations.domain.repository.ObligationAssignmentChangeRepository;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.Role;
import com.api.servicecompliance.serviceplanning.domain.model.ObligationDefinition;
import com.api.servicecompliance.serviceplanning.domain.model.RecurrenceType;
import com.api.servicecompliance.serviceplanning.domain.model.ServicePlan;
import com.api.servicecompliance.serviceplanning.domain.repository.ObligationDefinitionRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssignedObligationIntegrationTests {
    private static final Instant FIRST_START = Instant.parse("2030-01-02T13:00:00Z");

    @Autowired AssignedObligationQueryService assignedObligations;
    @Autowired ObligationRepository obligations;
    @Autowired ObligationAssignmentChangeRepository changes;
    @Autowired UserRepository users;
    @Autowired ServicePlanRepository plans;
    @Autowired ObligationDefinitionRepository definitions;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RefreshSessionRepository sessions;
    @Autowired RefreshFamilyRepository families;

    private User operator;
    private User otherOperator;

    @BeforeEach
    void setUp() {
        operator = saveUser("Operator", Role.OPERATOR);
        otherOperator = saveUser("Other operator", Role.OPERATOR);
    }

    @AfterEach
    void cleanUp() {
        changes.deleteAll();
        obligations.deleteAll();
        definitions.deleteAll();
        plans.deleteAll();
        sessions.deleteAll();
        families.deleteAll();
        users.deleteAll();
    }

    @Test
    void returnsOnlyOwnedWorkWithStableFilteredPaginationAndSourceVersions() {
        Obligation first = saveGenerated(operator.getId(), FIRST_START);
        Obligation second = saveGenerated(operator.getId(), FIRST_START);
        saveGenerated(otherOperator.getId(), FIRST_START.plusSeconds(1_800));

        var firstPage = assignedObligations.list(LocalDate.of(2030, 1, 2), LocalDate.of(2030, 1, 2),
                Set.of(ObligationStatus.ASSIGNED), null, 1, operator);

        assertThat(firstPage.items()).extracting(AssignedObligationQueryService.AssignedObligationView::id)
                .containsExactly(first.getId());
        assertThat(firstPage.items().get(0).sourcePlanId()).isNotNull();
        assertThat(firstPage.items().get(0).sourceDefinitionId()).isNotNull();
        assertThat(firstPage.items().get(0).timezone()).isEqualTo("America/Lima");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        var secondPage = assignedObligations.list(LocalDate.of(2030, 1, 2), LocalDate.of(2030, 1, 2),
                Set.of(ObligationStatus.ASSIGNED), firstPage.nextCursor(), 1, operator);
        assertThat(secondPage.items()).extracting(AssignedObligationQueryService.AssignedObligationView::id)
                .containsExactly(second.getId());
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.syncCursor()).isEqualTo(firstPage.syncCursor());

        assertThatThrownBy(() -> assignedObligations.list(null, null, Set.of(ObligationStatus.ASSIGNED),
                firstPage.nextCursor(), 1, operator)).isInstanceOf(FieldValidationException.class);
    }

    @Test
    void convergesReassignmentChangesWithoutLeakingFormerOperatorDetails() {
        Obligation obligation = saveGenerated(operator.getId(), FIRST_START);
        ObligationAssignmentChange original = null;
        if (!usesPostgres()) {
            original = changes.save(new ObligationAssignmentChange(1L, 1L, operator.getId(), obligation.getId(),
                    ObligationAssignmentChangeType.UPSERT, Instant.now()));
        }
        obligation.assignTo(otherOperator.getId());
        obligations.saveAndFlush(obligation);
        if (!usesPostgres()) {
            changes.save(new ObligationAssignmentChange(1L, 1L, operator.getId(), obligation.getId(),
                    ObligationAssignmentChangeType.REMOVED, Instant.now()));
            changes.save(new ObligationAssignmentChange(1L, 1L, otherOperator.getId(), obligation.getId(),
                    ObligationAssignmentChangeType.UPSERT, Instant.now()));
        }
        changes.save(new ObligationAssignmentChange(99L, 1L, operator.getId(), obligation.getId(),
                ObligationAssignmentChangeType.UPSERT, Instant.now()));

        var former = assignedObligations.changes(null, 10, operator);
        assertThat(former.changes()).hasSize(2).allSatisfy(change -> {
            assertThat(change.type()).isEqualTo(ObligationAssignmentChangeType.REMOVED);
            assertThat(change.obligation()).isNull();
        });
        assertThat(former.changes().get(0).cursor()).isNotEqualTo(former.changes().get(1).cursor());
        assertThat(former.changes().get(0).cursor()).isNotBlank();

        var current = assignedObligations.changes(null, 10, otherOperator);
        assertThat(current.changes()).hasSize(1);
        assertThat(current.changes().get(0).type()).isEqualTo(ObligationAssignmentChangeType.UPSERT);
        assertThat(current.changes().get(0).obligation().id()).isEqualTo(obligation.getId());
        if (original != null) assertThat(original.getId()).isNotNull();
    }

    @Test
    void derivesOverdueStatusForRowsAndStatusFilters() {
        Obligation overdue = saveGenerated(operator.getId(), Instant.parse("2020-01-02T13:00:00Z"));

        var overduePage = assignedObligations.list(null, null, Set.of(ObligationStatus.OVERDUE),
                null, 10, operator);
        var assignedPage = assignedObligations.list(null, null, Set.of(ObligationStatus.ASSIGNED),
                null, 10, operator);

        assertThat(overduePage.items()).extracting(AssignedObligationQueryService.AssignedObligationView::id)
                .contains(overdue.getId());
        assertThat(overduePage.items()).filteredOn(item -> item.id().equals(overdue.getId()))
                .extracting(AssignedObligationQueryService.AssignedObligationView::status)
                .containsExactly(ObligationStatus.OVERDUE);
        assertThat(assignedPage.items()).extracting(AssignedObligationQueryService.AssignedObligationView::id)
                .doesNotContain(overdue.getId());
    }

    @Test
    void exposesTheNewEndpointWithoutChangingTheLegacyListAndRejectsNonOperators() throws Exception {
        Obligation obligation = saveGenerated(operator.getId(), FIRST_START);
        User supervisor = saveUser("Supervisor", Role.SUPERVISOR);

        mvc.perform(get("/api/v1/assigned-obligations")
                        .header("Authorization", "Bearer " + login(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(obligation.getId()))
                .andExpect(jsonPath("$.items[0].activity").value("Clean lobby"))
                .andExpect(jsonPath("$.syncCursor").isString());
        mvc.perform(get("/api/v1/obligations")
                        .header("Authorization", "Bearer " + login(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(obligation.getId()));
        mvc.perform(get("/api/v1/assigned-obligations")
                        .header("Authorization", "Bearer " + login(supervisor)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assigned-obligations?limit=101")
                        .header("Authorization", "Bearer " + login(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThatThrownBy(() -> assignedObligations.list(null, null, null, null, 10, supervisor))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Obligation saveGenerated(Long operatorId, Instant startsAt) {
        LocalDate occurrenceDate = LocalDate.ofInstant(startsAt, java.time.ZoneId.of("America/Lima"));
        ServicePlan plan = plans.saveAndFlush(new ServicePlan(1L, 1L, 1L, occurrenceDate,
                occurrenceDate.plusDays(1), operatorId, Instant.now()));
        ObligationDefinition definition = definitions.saveAndFlush(new ObligationDefinition(plan.getId(), 1L,
                "Clean lobby", "Use approved product", "No visible residue", RecurrenceType.ONCE,
                occurrenceDate, 1, Set.of(), LocalTime.ofInstant(startsAt, java.time.ZoneId.of("America/Lima")),
                30, List.of(), operatorId, Instant.now()));
        Obligation obligation = new Obligation("Clean lobby", "Use approved product", "Site 1", startsAt,
                startsAt.plusSeconds(1_800), 1L, 1L, plan.getId(), plan.getBusinessVersion(), definition.getId(),
                definition.getVersion(), occurrenceDate, List.of(), List.of());
        obligation.assignTo(operatorId);
        return obligations.saveAndFlush(obligation);
    }

    private User saveUser(String name, Role role) {
        String email = UUID.randomUUID() + "@assigned.test";
        return users.saveAndFlush(new User(name, email, passwords.encode("Password123!"), role, 1L, 1L));
    }

    private String login(User user) throws Exception {
        String body = "{\"email\":\"" + user.getEmail() + "\",\"password\":\"Password123!\"}";
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("token").asText();
    }

    private boolean usesPostgres() {
        try (var connection = java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            return connection.getMetaData().getDatabaseProductName().contains("PostgreSQL");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
