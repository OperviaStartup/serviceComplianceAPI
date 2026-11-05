package com.api.servicecompliance.serviceplanning.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.serviceplanning.domain.model.ServicePlan;
import com.api.servicecompliance.serviceplanning.domain.model.ServicePlanIdempotency;
import com.api.servicecompliance.serviceplanning.domain.model.ServicePlanState;
import com.api.servicecompliance.serviceplanning.domain.repository.CompanyServiceRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanIdempotencyRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ServicePlanRepository;
import com.api.servicecompliance.serviceplanning.domain.repository.ObligationDefinitionRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.Role;
import com.api.servicecompliance.shared.domain.ScopedAccessDeniedException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ServicePlanService {
    private static final String CREATE_OPERATION = "CREATE_SERVICE_PLAN";
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(7);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");
    private static final List<String> INITIAL_REQUIREMENTS = List.of("OBLIGATION_DEFINITION_REQUIRED");

    private final ServicePlanRepository plans;
    private final CompanyServiceRepository companyServices;
    private final ServicePlanIdempotencyRepository idempotencyRecords;
    private final SiteAuthorizationPort sites;
    private final ObligationDefinitionRepository definitions;
    private final TransactionTemplate transactions;

    public ServicePlanService(ServicePlanRepository plans, CompanyServiceRepository companyServices,
                              ServicePlanIdempotencyRepository idempotencyRecords,
                              SiteAuthorizationPort sites, ObligationDefinitionRepository definitions,
                              PlatformTransactionManager transactionManager) {
        this.plans = plans;
        this.companyServices = companyServices;
        this.idempotencyRecords = idempotencyRecords;
        this.sites = sites;
        this.definitions = definitions;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ServicePlanResponse create(CreateServicePlanRequest request, String idempotencyKey, User actor) {
        requireAdministrator(actor);
        validateIdempotencyKey(idempotencyKey);
        validatePeriod(request.startDate(), request.endDate());
        String requestHash = hash(request);
        try {
            return transactions.execute(status -> createInTransaction(request, idempotencyKey, requestHash, actor));
        } catch (DataIntegrityViolationException exception) {
            ServicePlanResponse replay = transactions.execute(status -> replayAfterConcurrentRequest(
                    actor, idempotencyKey, requestHash));
            if (replay != null) {
                return replay;
            }
            throw exception;
        }
    }

    public ServicePlanResponse find(Long id, User actor) {
        requireAdministrator(actor);
        return plans.findByIdAndCompanyId(id, actor.getCompanyId())
                .map(this::response)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de servicio no encontrado"));
    }

    private ServicePlanResponse createInTransaction(CreateServicePlanRequest request, String idempotencyKey,
                                                     String requestHash, User actor) {
        Instant now = Instant.now();
        var existing = idempotencyRecords.findByCompanyIdAndActorIdAndOperationAndIdempotencyKey(
                actor.getCompanyId(), actor.getId(), CREATE_OPERATION, idempotencyKey);
        if (existing.isPresent() && existing.get().getExpiresAt().isAfter(now)) {
            return replay(existing.get(), requestHash);
        }
        existing.ifPresent(record -> {
            idempotencyRecords.delete(record);
            idempotencyRecords.flush();
        });
        requireAuthorizedReferences(request, actor.getCompanyId());
        ServicePlan plan = plans.saveAndFlush(new ServicePlan(actor.getCompanyId(), request.serviceId(),
                request.siteId(), request.startDate(), request.endDate(), actor.getId(), now));
        idempotencyRecords.saveAndFlush(new ServicePlanIdempotency(actor.getCompanyId(), actor.getId(),
                CREATE_OPERATION, idempotencyKey, requestHash, plan.getId(), now,
                now.plus(IDEMPOTENCY_RETENTION)));
        return response(plan);
    }

    private ServicePlanResponse replayAfterConcurrentRequest(User actor, String idempotencyKey,
                                                              String requestHash) {
        return idempotencyRecords.findByCompanyIdAndActorIdAndOperationAndIdempotencyKey(
                        actor.getCompanyId(), actor.getId(), CREATE_OPERATION, idempotencyKey)
                .filter(record -> record.getExpiresAt().isAfter(Instant.now()))
                .map(record -> replay(record, requestHash))
                .orElse(null);
    }

    private ServicePlanResponse replay(ServicePlanIdempotency record, String requestHash) {
        if (!MessageDigest.isEqual(record.getRequestHash().getBytes(StandardCharsets.US_ASCII),
                requestHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new ApiConflictException("IDEMPOTENCY_KEY_MISMATCH",
                    "La clave de idempotencia ya fue utilizada con una solicitud diferente");
        }
        return plans.findById(record.getServicePlanId())
                .map(this::response)
                .orElseThrow(() -> new IllegalStateException("Idempotency record without service plan"));
    }

    private void requireAuthorizedReferences(CreateServicePlanRequest request, Long companyId) {
        var details = new java.util.LinkedHashMap<String, List<String>>();
        if (!sites.belongsToCompany(request.siteId(), companyId)) {
            details.put("siteId", List.of("no pertenece al alcance de la empresa autenticada"));
        }
        if (!companyServices.existsByIdAndCompanyIdAndActiveTrue(request.serviceId(), companyId)) {
            details.put("serviceId", List.of("no pertenece al alcance de la empresa autenticada"));
        }
        if (!details.isEmpty()) {
            throw new ScopedAccessDeniedException(details);
        }
    }

    private static void requireAdministrator(User actor) {
        if (actor == null || !actor.isActive() || actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Administrator role required");
        }
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new FieldValidationException("Datos inválidos", Map.of("idempotencyKey", List.of(
                    "debe contener entre 8 y 100 caracteres alfanuméricos o . _ : -")));
        }
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new FieldValidationException("Datos inválidos",
                    Map.of("endDate", List.of("debe ser igual o posterior a startDate")));
        }
    }

    private static String hash(CreateServicePlanRequest request) {
        String canonical = request.serviceId() + "|" + request.siteId() + "|" + request.startDate()
                + "|" + request.endDate() + "|" + ServicePlan.TIMEZONE;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ServicePlanResponse response(ServicePlan plan) {
        List<String> missingRequirements = definitions.existsByServicePlanId(plan.getId())
                ? List.of() : INITIAL_REQUIREMENTS;
        return ServicePlanResponse.from(plan, missingRequirements);
    }

    public record CreateServicePlanRequest(
            @NotNull @Positive Long serviceId,
            @NotNull @Positive Long siteId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    public record ServicePlanResponse(
            Long id,
            Long companyId,
            Long serviceId,
            Long siteId,
            LocalDate startDate,
            LocalDate endDate,
            String timezone,
            ServicePlanState state,
            Long createdBy,
            Instant createdAt,
            Instant updatedAt,
            long version,
            List<String> missingActivationRequirements) {
        static ServicePlanResponse from(ServicePlan plan, List<String> missingRequirements) {
            return new ServicePlanResponse(plan.getId(), plan.getCompanyId(), plan.getServiceId(),
                    plan.getSiteId(), plan.getStartDate(), plan.getEndDate(), plan.getTimezone(),
                    plan.getState(), plan.getCreatedBy(), plan.getCreatedAt(), plan.getUpdatedAt(),
                    plan.getVersion(), missingRequirements);
        }
    }
}
