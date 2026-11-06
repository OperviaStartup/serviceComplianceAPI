package com.api.servicecompliance.serviceplanning.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.serviceplanning.domain.model.*;
import com.api.servicecompliance.serviceplanning.domain.repository.*;
import com.api.servicecompliance.shared.domain.*;
import com.api.servicecompliance.shared.domain.Role;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ServicePlanActivationService {
    private final ServicePlanRepository plans;
    private final CompanyServiceRepository companyServices;
    private final ObligationDefinitionRepository definitions;

    public ServicePlanActivationService(ServicePlanRepository plans, CompanyServiceRepository companyServices,
                                        ObligationDefinitionRepository definitions) {
        this.plans = plans;
        this.companyServices = companyServices;
        this.definitions = definitions;
    }

    @Transactional
    public PlanVersionResponse activate(Long planId, VersionCommand command, User actor) {
        requireAdministrator(actor);
        ServicePlan plan = lockedPlan(planId, actor.getCompanyId());
        if (plan.isActive()) return PlanVersionResponse.from(plan);
        if (!plan.isDraft()) throw stateConflict(plan, "Solo se puede activar un plan en borrador");
        requireExpectedVersion(plan, command.expectedVersion());
        companyServices.lockActiveByIdAndCompanyId(plan.getServiceId(), plan.getCompanyId())
                .orElseThrow(() -> new ApiConflictException("SERVICE_NOT_ACTIVE", "El servicio ya no está activo"));
        var planDefinitions = definitions.findAllByServicePlanId(planId);
        if (planDefinitions.isEmpty()
                || planDefinitions.stream().anyMatch(definition -> definition.getEvidenceRequirements().isEmpty())) {
            throw new ApiConflictException("ACTIVATION_REQUIREMENTS_MISSING",
                    "El plan requiere definiciones válidas y reglas de evidencia", plan.getVersion());
        }
        if (plans.existsActiveOverlap(plan.getCompanyId(), plan.getServiceId(), plan.getSiteId(),
                plan.getSeriesId(), plan.getStartDate(), plan.getEndDate())) {
            throw new ApiConflictException("PLAN_PERIOD_OVERLAP",
                    "Existe otro plan activo para el mismo servicio y sede", plan.getVersion());
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (plan.getSupersedesPlanId() != null) {
            ServicePlan predecessor = lockedPlan(plan.getSupersedesPlanId(), actor.getCompanyId());
            if (!predecessor.isActive() || !predecessor.getSeriesId().equals(plan.getSeriesId())) {
                throw stateConflict(plan, "La versión predecesora ya no está activa");
            }
            predecessor.supersede(now);
            plans.save(predecessor);
        }
        plan.activate(actor.getId(), now);
        return PlanVersionResponse.from(plans.saveAndFlush(plan));
    }

    @Transactional
    public PlanVersionResponse amend(Long planId, VersionCommand command, User actor) {
        requireAdministrator(actor);
        ServicePlan source = lockedPlan(planId, actor.getCompanyId());
        if (!source.isActive()) throw stateConflict(source, "Solo se puede enmendar una versión activa");
        requireExpectedVersion(source, command.expectedVersion());
        if (plans.existsBySupersedesPlanIdAndState(source.getId(), ServicePlanState.DRAFT)) {
            throw new ApiConflictException("AMENDMENT_ALREADY_EXISTS",
                    "Ya existe una enmienda en borrador", source.getVersion());
        }
        var sourceDefinitions = definitions.findAllByServicePlanId(source.getId());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ServicePlan amendment = plans.saveAndFlush(new ServicePlan(source, actor.getId(), now));
        definitions.saveAllAndFlush(sourceDefinitions.stream()
                .map(definition -> definition.copyFor(amendment.getId(), actor.getId(), now)).toList());
        return PlanVersionResponse.from(amendment);
    }

    private ServicePlan lockedPlan(Long planId, Long companyId) {
        return plans.lockByIdAndCompanyId(planId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de servicio no encontrado"));
    }

    private static void requireExpectedVersion(ServicePlan plan, long expectedVersion) {
        if (plan.getVersion() != expectedVersion) {
            throw new ApiConflictException("VERSION_CONFLICT", "El plan fue modificado", plan.getVersion());
        }
    }

    private static ApiConflictException stateConflict(ServicePlan plan, String message) {
        return new ApiConflictException("INVALID_PLAN_STATE", message, plan.getVersion());
    }

    private static void requireAdministrator(User actor) {
        if (actor == null || !actor.isActive() || actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Administrator role required");
        }
    }

    public record VersionCommand(@NotNull Long expectedVersion) {}

    public record PlanVersionResponse(Long id, UUID seriesId, int businessVersion, Long supersedesPlanId,
                                      ServicePlanState state, Long activatedBy, Instant activatedAt,
                                      long version) {
        static PlanVersionResponse from(ServicePlan plan) {
            return new PlanVersionResponse(plan.getId(), plan.getSeriesId(), plan.getBusinessVersion(),
                    plan.getSupersedesPlanId(), plan.getState(), plan.getActivatedBy(), plan.getActivatedAt(),
                    plan.getVersion());
        }
    }
}
