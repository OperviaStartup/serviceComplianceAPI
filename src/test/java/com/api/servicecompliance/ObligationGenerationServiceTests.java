package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.obligations.application.ObligationGenerationService;
import com.api.servicecompliance.serviceplanning.domain.model.*;
import com.api.servicecompliance.serviceplanning.domain.repository.ObligationDefinitionRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanRepository;
import com.api.servicecompliance.shared.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObligationGenerationServiceTests {
    @Mock ServicePlanRepository plans;
    @Mock ObligationDefinitionRepository definitions;
    @Mock ObligationRepository obligations;

    @Test
    void generatesAnOccurrenceOnceAndKeepsItsSourceVersion() {
        Instant now = Instant.parse("2026-11-06T12:00:00Z");
        ServicePlan plan = new ServicePlan(1L, 1L, 1L,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 7L, now);
        plan.activate(7L, now);
        ReflectionTestUtils.setField(plan, "id", 11L);
        ObligationDefinition definition = new ObligationDefinition(1L, 1L, "Clean lobby", "Use approved product",
                "No visible residue", RecurrenceType.ONCE, LocalDate.of(2026, 11, 10), 1, Set.of(),
                LocalTime.of(8, 0), 60,
                List.of(new EvidenceRequirement(EvidenceRequirementType.PHOTO, true, 1, 1, Map.of())), 7L, now);
        ReflectionTestUtils.setField(definition, "id", 22L);
        ReflectionTestUtils.setField(definition, "version", 3L);
        User admin = new User("Admin", "admin@test.com", "hash", Role.ADMIN, 1L, 1L);
        when(plans.lockByIdAndCompanyId(any(), eq(1L))).thenReturn(Optional.of(plan));
        when(definitions.findAllByServicePlanId(any())).thenReturn(List.of(definition));
        when(obligations.findBySourceDefinitionIdAndOccurrenceDate(any(), any())).thenReturn(Optional.empty());
        when(obligations.saveAndFlush(any(Obligation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ObligationGenerationService.GenerationResponse response = new ObligationGenerationService(plans, definitions, obligations)
                .generate(1L, new ObligationGenerationService.GenerationRequest(
                        LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10)), admin);

        assertThat(response.generated()).isEqualTo(1);
        ArgumentCaptor<Obligation> captured = ArgumentCaptor.forClass(Obligation.class);
        verify(obligations).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getSourcePlanId()).isEqualTo(plan.getId());
        assertThat(captured.getValue().getSourcePlanVersion()).isEqualTo(plan.getBusinessVersion());
        assertThat(captured.getValue().getSourceDefinitionId()).isEqualTo(definition.getId());
        assertThat(captured.getValue().getSourceDefinitionVersion()).isEqualTo(definition.getVersion());
        assertThat(captured.getValue().getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(captured.getValue().getAssignedOperatorId()).isNull();
    }

    @Test
    void rerunningTheSameOccurrenceDoesNotCreateAnotherObligation() {
        Instant now = Instant.parse("2026-11-06T12:00:00Z");
        ServicePlan plan = new ServicePlan(1L, 1L, 1L,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 7L, now);
        plan.activate(7L, now);
        ReflectionTestUtils.setField(plan, "id", 11L);
        ObligationDefinition definition = new ObligationDefinition(1L, 1L, "Clean lobby", "Instructions",
                "Criteria", RecurrenceType.ONCE, LocalDate.of(2026, 11, 10), 1, Set.of(),
                LocalTime.of(8, 0), 60, List.of(new EvidenceRequirement(
                EvidenceRequirementType.PHOTO, true, 1, 1, Map.of())), 7L, now);
        ReflectionTestUtils.setField(definition, "id", 22L);
        User admin = new User("Admin", "admin@test.com", "hash", Role.ADMIN, 1L, 1L);
        when(plans.lockByIdAndCompanyId(any(), eq(1L))).thenReturn(Optional.of(plan));
        when(definitions.findAllByServicePlanId(any())).thenReturn(List.of(definition));
        when(obligations.findBySourceDefinitionIdAndOccurrenceDate(any(), any()))
                .thenReturn(Optional.of(new Obligation("Existing", "Existing", "Site 1", null,
                        Instant.now(), 1L, 1L)));

        ObligationGenerationService.GenerationResponse response = new ObligationGenerationService(plans, definitions, obligations)
                .generate(1L, new ObligationGenerationService.GenerationRequest(
                        LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10)), admin);

        assertThat(response.generated()).isZero();
        verify(obligations, never()).saveAndFlush(any());
    }

    @Test
    void recoversMissedDailyOccurrencesUsingLimaLocalTime() {
        Instant now = Instant.parse("2026-11-06T12:00:00Z");
        ServicePlan plan = new ServicePlan(1L, 1L, 1L,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 7L, now);
        plan.activate(7L, now);
        ReflectionTestUtils.setField(plan, "id", 11L);
        ObligationDefinition definition = new ObligationDefinition(11L, 1L, "Clean lobby", "Instructions",
                "Criteria", RecurrenceType.DAILY, null, 1, Set.of(), LocalTime.of(8, 0), 60,
                List.of(), 7L, now);
        ReflectionTestUtils.setField(definition, "id", 22L);
        User admin = new User("Admin", "admin@test.com", "hash", Role.ADMIN, 1L, 1L);
        when(plans.lockByIdAndCompanyId(11L, 1L)).thenReturn(Optional.of(plan));
        when(definitions.findAllByServicePlanId(11L)).thenReturn(List.of(definition));
        when(obligations.findBySourceDefinitionIdAndOccurrenceDate(eq(22L), any())).thenReturn(Optional.empty());
        when(obligations.saveAndFlush(any(Obligation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = new ObligationGenerationService(plans, definitions, obligations).generate(11L,
                new ObligationGenerationService.GenerationRequest(
                        LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4)), admin);

        assertThat(response.generated()).isEqualTo(3);
        ArgumentCaptor<Obligation> captured = ArgumentCaptor.forClass(Obligation.class);
        verify(obligations, times(3)).saveAndFlush(captured.capture());
        assertThat(captured.getAllValues()).extracting(Obligation::getOccurrenceDate)
                .containsExactly(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3),
                        LocalDate.of(2026, 11, 4));
        assertThat(captured.getAllValues().get(0).getStartsAt())
                .isEqualTo(Instant.parse("2026-11-02T13:00:00Z"));
    }
}
