package com.api.servicecompliance.serviceplanning.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.serviceplanning.domain.model.*;
import com.api.servicecompliance.serviceplanning.domain.repository.ObligationDefinitionRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanRepository;
import com.api.servicecompliance.shared.domain.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class ObligationDefinitionService {
    private static final Map<EvidenceRequirementType, Set<String>> ALLOWED_CONSTRAINTS = Map.of(
            EvidenceRequirementType.PHOTO, Set.of("requireGeolocation", "maxAgeMinutes"),
            EvidenceRequirementType.QR_CODE, Set.of("expectedPrefix"),
            EvidenceRequirementType.LOCATION, Set.of("maxAccuracyMeters"),
            EvidenceRequirementType.SIGNATURE, Set.of("signerLabel"));

    private final ServicePlanRepository plans;
    private final ObligationDefinitionRepository definitions;
    private final SiteAuthorizationPort sites;

    public ObligationDefinitionService(ServicePlanRepository plans,
                                       ObligationDefinitionRepository definitions,
                                       SiteAuthorizationPort sites) {
        this.plans = plans;
        this.definitions = definitions;
        this.sites = sites;
    }

    @Transactional
    public DefinitionResponse create(Long planId, DefinitionRequest request, User actor) {
        ServicePlan plan = editablePlan(planId, actor);
        validate(request, plan, actor.getCompanyId(), false);
        Instant now = Instant.now();
        ObligationDefinition definition = new ObligationDefinition(planId, request.siteId(),
                request.activity().trim(), request.instructions().trim(), request.acceptanceCriteria().trim(),
                request.recurrenceType(), request.occurrenceDate(), request.recurrenceInterval(),
                normalizedDays(request.daysOfWeek()), request.localStartTime(), request.windowDurationMinutes(),
                requirements(request.evidenceRequirements()), actor.getId(), now);
        return DefinitionResponse.from(definitions.saveAndFlush(definition));
    }

    @Transactional
    public DefinitionResponse update(Long planId, Long definitionId, DefinitionRequest request, User actor) {
        ServicePlan plan = editablePlan(planId, actor);
        ObligationDefinition definition = definitions.findByIdAndServicePlanId(definitionId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Definición de obligación no encontrada"));
        validate(request, plan, actor.getCompanyId(), true);
        if (request.expectedVersion() != definition.getVersion()) {
            throw new ApiConflictException("VERSION_CONFLICT", "La definición fue modificada", definition.getVersion());
        }
        definition.update(request.activity().trim(), request.instructions().trim(),
                request.acceptanceCriteria().trim(), request.recurrenceType(), request.occurrenceDate(),
                request.recurrenceInterval(), normalizedDays(request.daysOfWeek()), request.localStartTime(),
                request.windowDurationMinutes(), requirements(request.evidenceRequirements()),
                actor.getId(), Instant.now());
        return DefinitionResponse.from(definitions.saveAndFlush(definition));
    }

    private ServicePlan editablePlan(Long planId, User actor) {
        requireAdministrator(actor);
        ServicePlan plan = plans.findByIdAndCompanyId(planId, actor.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan de servicio no encontrado"));
        if (!plan.isDraft()) {
            throw new ApiConflictException("PLAN_NOT_DRAFT", "Solo se pueden modificar planes en borrador",
                    plan.getVersion());
        }
        return plan;
    }

    private void validate(DefinitionRequest request, ServicePlan plan, Long companyId, boolean update) {
        var errors = new LinkedHashMap<String, List<String>>();
        if (!request.siteId().equals(plan.getSiteId()) || !sites.belongsToCompany(request.siteId(), companyId)) {
            throw new ScopedAccessDeniedException(Map.of("siteId",
                    List.of("no pertenece al plan y empresa autenticados")));
        }
        Set<DayOfWeek> days = normalizedDays(request.daysOfWeek());
        switch (request.recurrenceType()) {
            case ONCE -> {
                if (request.occurrenceDate() == null || request.occurrenceDate().isBefore(plan.getStartDate())
                        || request.occurrenceDate().isAfter(plan.getEndDate())) {
                    errors.put("occurrenceDate", List.of("debe estar dentro del periodo inclusivo del plan"));
                }
                if (!days.isEmpty()) errors.put("daysOfWeek", List.of("debe estar vacío para ONCE"));
                if (request.recurrenceInterval() != 1) errors.put("recurrenceInterval", List.of("debe ser 1 para ONCE"));
            }
            case DAILY -> {
                if (request.occurrenceDate() != null) errors.put("occurrenceDate", List.of("debe estar vacío para DAILY"));
                if (!days.isEmpty()) errors.put("daysOfWeek", List.of("debe estar vacío para DAILY"));
            }
            case WEEKLY -> {
                if (request.occurrenceDate() != null) errors.put("occurrenceDate", List.of("debe estar vacío para WEEKLY"));
                if (days.isEmpty()) errors.put("daysOfWeek", List.of("requiere al menos un día para WEEKLY"));
            }
        }
        if (update && request.expectedVersion() == null) {
            errors.put("expectedVersion", List.of("es obligatorio para editar"));
        }
        validateEvidence(request.evidenceRequirements(), errors);
        if (!errors.isEmpty()) throw new FieldValidationException("Datos inválidos", errors);
    }

    private static void validateEvidence(List<EvidenceRequirementRequest> requests,
                                         Map<String, List<String>> errors) {
        if (requests == null || requests.isEmpty()) {
            errors.put("evidenceRequirements", List.of("requiere al menos una regla"));
            return;
        }
        Set<EvidenceRequirementType> types = EnumSet.noneOf(EvidenceRequirementType.class);
        for (int index = 0; index < requests.size(); index++) {
            EvidenceRequirementRequest request = requests.get(index);
            String field = "evidenceRequirements[" + index + "]";
            if (!types.add(request.type())) errors.put(field + ".type", List.of("no puede repetirse"));
            if (request.required() && request.minimumCount() < 1) {
                errors.put(field + ".minimumCount", List.of("debe ser al menos 1 cuando es obligatorio"));
            }
            if (!request.required() && request.minimumCount() != 0) {
                errors.put(field + ".minimumCount", List.of("debe ser 0 cuando es opcional"));
            }
            if (request.minimumCount() < 0 || request.maximumCount() < 1
                    || request.maximumCount() > 10 || request.maximumCount() < request.minimumCount()) {
                errors.put(field + ".maximumCount", List.of("debe estar entre minimumCount y 10"));
            }
            Map<String, String> constraints = request.constraints() == null ? Map.of() : request.constraints();
            for (var constraint : constraints.entrySet()) {
                if (!ALLOWED_CONSTRAINTS.get(request.type()).contains(constraint.getKey())
                        || !validConstraint(request.type(), constraint.getKey(), constraint.getValue())) {
                    errors.put(field + ".constraints." + constraint.getKey(), List.of("regla inválida para el tipo"));
                }
            }
        }
    }

    private static boolean validConstraint(EvidenceRequirementType type, String key, String value) {
        if (value == null || value.isBlank() || value.length() > 255) return false;
        try {
            return switch (key) {
                case "requireGeolocation" -> value.equals("true") || value.equals("false");
                case "maxAgeMinutes" -> Integer.parseInt(value) >= 1 && Integer.parseInt(value) <= 1440;
                case "maxAccuracyMeters" -> new java.math.BigDecimal(value).compareTo(java.math.BigDecimal.ONE) >= 0
                        && new java.math.BigDecimal(value).compareTo(new java.math.BigDecimal("1000")) <= 0;
                case "expectedPrefix", "signerLabel" -> value.length() <= 100;
                default -> false;
            };
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static List<EvidenceRequirement> requirements(List<EvidenceRequirementRequest> requests) {
        return requests.stream().map(request -> new EvidenceRequirement(request.type(), request.required(),
                request.minimumCount(), request.maximumCount(),
                request.constraints() == null ? Map.of() : request.constraints())).toList();
    }

    private static Set<DayOfWeek> normalizedDays(Set<DayOfWeek> days) {
        return days == null || days.isEmpty() ? Set.of() : EnumSet.copyOf(days);
    }

    private static void requireAdministrator(User actor) {
        if (actor == null || !actor.isActive() || actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Administrator role required");
        }
    }

    public record DefinitionRequest(
            @NotNull @Positive Long siteId,
            @NotBlank @Size(max = 255) String activity,
            @NotBlank @Size(max = 2000) String instructions,
            @NotBlank @Size(max = 2000) String acceptanceCriteria,
            @NotNull RecurrenceType recurrenceType,
            LocalDate occurrenceDate,
            @Min(1) @Max(30) int recurrenceInterval,
            Set<DayOfWeek> daysOfWeek,
            @NotNull LocalTime localStartTime,
            @Min(1) @Max(1440) int windowDurationMinutes,
            @NotNull @Size(min = 1, max = 10) List<@Valid EvidenceRequirementRequest> evidenceRequirements,
            Long expectedVersion) {}

    public record EvidenceRequirementRequest(
            @NotNull EvidenceRequirementType type,
            boolean required,
            @Min(0) int minimumCount,
            @Min(1) @Max(10) int maximumCount,
            @Size(max = 4) Map<@NotBlank @Size(max = 50) String, @NotBlank @Size(max = 255) String> constraints) {}

    public record DefinitionResponse(Long id, Long servicePlanId, Long siteId, String activity,
                                     String instructions, String acceptanceCriteria, RecurrenceType recurrenceType,
                                     LocalDate occurrenceDate, int recurrenceInterval, Set<DayOfWeek> daysOfWeek,
                                     LocalTime localStartTime, int windowDurationMinutes, String timezone,
                                     List<EvidenceRequirementResponse> evidenceRequirements, Long createdBy,
                                     Long updatedBy, Instant createdAt, Instant updatedAt, long version) {
        static DefinitionResponse from(ObligationDefinition definition) {
            return new DefinitionResponse(definition.getId(), definition.getServicePlanId(), definition.getSiteId(),
                    definition.getActivity(), definition.getInstructions(), definition.getAcceptanceCriteria(),
                    definition.getRecurrenceType(), definition.getOccurrenceDate(), definition.getRecurrenceInterval(),
                    definition.getDaysOfWeek(), definition.getLocalStartTime(), definition.getWindowDurationMinutes(),
                    definition.getTimezone(), definition.getEvidenceRequirements().stream()
                    .map(EvidenceRequirementResponse::from).toList(), definition.getCreatedBy(),
                    definition.getUpdatedBy(), definition.getCreatedAt(), definition.getUpdatedAt(), definition.getVersion());
        }
    }

    public record EvidenceRequirementResponse(Long id, EvidenceRequirementType type, boolean required,
                                              int minimumCount, int maximumCount, Map<String, String> constraints) {
        static EvidenceRequirementResponse from(EvidenceRequirement requirement) {
            return new EvidenceRequirementResponse(requirement.getId(), requirement.getEvidenceType(),
                    requirement.isRequired(), requirement.getMinimumCount(), requirement.getMaximumCount(),
                    requirement.getConstraints());
        }
    }
}
