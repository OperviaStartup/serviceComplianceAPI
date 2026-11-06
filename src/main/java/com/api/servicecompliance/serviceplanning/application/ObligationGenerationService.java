package com.api.servicecompliance.serviceplanning.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.obligations.domain.model.*;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.serviceplanning.domain.model.*;
import com.api.servicecompliance.serviceplanning.domain.repository.ObligationDefinitionRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.Role;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ObligationGenerationService {
    private final ServicePlanRepository plans;
    private final ObligationDefinitionRepository definitions;
    private final ObligationRepository obligations;

    public ObligationGenerationService(ServicePlanRepository plans, ObligationDefinitionRepository definitions,
                                       ObligationRepository obligations) {
        this.plans = plans;
        this.definitions = definitions;
        this.obligations = obligations;
    }

    @Transactional
    public GenerationResponse generate(Long planId, GenerationRequest request, User actor) {
        requireAdministrator(actor);
        if (request.fromDate().isAfter(request.toDate())) {
            throw new FieldValidationException("Datos inválidos",
                    Map.of("fromDate", List.of("debe ser anterior o igual a toDate")));
        }
        ServicePlan plan = plans.lockByIdAndCompanyId(planId, actor.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan de servicio no encontrado"));
        if (!plan.isActive()) {
            throw new ApiConflictException("INVALID_PLAN_STATE", "Solo se pueden generar obligaciones de un plan activo",
                    plan.getVersion());
        }
        if (request.fromDate().isBefore(plan.getStartDate()) || request.toDate().isAfter(plan.getEndDate())) {
            throw new FieldValidationException("Datos inválidos",
                    Map.of("dateRange", List.of("debe estar dentro del periodo inclusivo del plan")));
        }

        int generated = 0;
        List<GeneratedObligation> created = new ArrayList<>();
        for (ObligationDefinition definition : definitions.findAllByServicePlanId(planId)) {
            for (LocalDate date = request.fromDate(); !date.isAfter(request.toDate()); date = date.plusDays(1)) {
                if (!occurs(definition, plan, date) || obligations.findBySourceDefinitionIdAndOccurrenceDate(definition.getId(), date).isPresent()) {
                    continue;
                }
                ZoneId zone = ZoneId.of(definition.getTimezone());
                Instant startsAt = ZonedDateTime.of(date, definition.getLocalStartTime(), zone).toInstant();
                Instant dueAt = startsAt.plus(definition.getWindowDurationMinutes(), ChronoUnit.MINUTES);
                List<ObligationStep> steps = List.of(new ObligationStep(definition.getActivity(),
                        definition.getInstructions(), 1));
                List<ObligationPhotoRequirement> photos = new ArrayList<>();
                int position = 1;
                for (EvidenceRequirement requirement : definition.getEvidenceRequirements()) {
                    if (requirement.getEvidenceType() == EvidenceRequirementType.PHOTO && requirement.isRequired()) {
                        photos.add(new ObligationPhotoRequirement("Photo requirement " + position,
                                "Generated from the service-plan definition", position++));
                    }
                }
                Obligation obligation = new Obligation(definition.getActivity(), definition.getInstructions(),
                        "Site " + plan.getSiteId(), startsAt, dueAt, plan.getCompanyId(), plan.getSiteId(),
                        plan.getId(), plan.getBusinessVersion(), definition.getId(), definition.getVersion(),
                        date, steps, photos);
                Obligation saved = obligations.saveAndFlush(obligation);
                created.add(new GeneratedObligation(saved.getId(), definition.getId(), date, saved.getStartsAt(), saved.getDueAt()));
                generated++;
            }
        }
        return new GenerationResponse(plan.getId(), request.fromDate(), request.toDate(), generated, created);
    }

    private static boolean occurs(ObligationDefinition definition, ServicePlan plan, LocalDate date) {
        return switch (definition.getRecurrenceType()) {
            case ONCE -> definition.getOccurrenceDate().equals(date);
            case DAILY -> ChronoUnit.DAYS.between(plan.getStartDate(), date) % definition.getRecurrenceInterval() == 0;
            case WEEKLY -> definition.getDaysOfWeek().contains(date.getDayOfWeek())
                    && ChronoUnit.WEEKS.between(plan.getStartDate(), date) % definition.getRecurrenceInterval() == 0;
        };
    }

    private static void requireAdministrator(User actor) {
        if (actor == null || !actor.isActive() || actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Administrator role required");
        }
    }

    public record GenerationRequest(@NotNull LocalDate fromDate, @NotNull LocalDate toDate) {}
    public record GeneratedObligation(Long id, Long definitionId, LocalDate occurrenceDate,
                                      Instant startsAt, Instant dueAt) {}
    public record GenerationResponse(Long planId, LocalDate fromDate, LocalDate toDate,
                                     int generated, List<GeneratedObligation> obligations) {}
}
