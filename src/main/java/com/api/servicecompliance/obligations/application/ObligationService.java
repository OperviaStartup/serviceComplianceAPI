package com.api.servicecompliance.obligations.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.obligations.domain.model.*;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class ObligationService {
    private final ObligationRepository repository;
    private final UserRepository users;
    private final Clock clock;

    public ObligationService(ObligationRepository repository, UserRepository users, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public ObligationDetail create(CreateObligation request, User actor) {
        if (actor.getRole() != Role.SUPERVISOR && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Forbidden");
        }
        User assignee = users.findById(request.assignedOperatorId())
                .orElseThrow(() -> new AccessDeniedException("Forbidden"));
        if (!assignee.isActive() || assignee.getRole() != Role.OPERATOR
                || !actor.getCompanyId().equals(assignee.getCompanyId())
                || !actor.getSiteId().equals(assignee.getSiteId())) {
            throw new AccessDeniedException("Forbidden");
        }
        validateExecutionDetails(request);
        Obligation obligation;
        if (request.startsAt() == null) {
            // Preserve the legacy creation contract without inventing execution details.
            obligation = new Obligation(request.title(), request.description(), request.siteName(),
                    request.assignedOperatorId(), request.dueAt(), actor.getCompanyId(), actor.getSiteId());
        } else {
            var steps = new ArrayList<ObligationStep>();
            for (int index = 0; index < request.steps().size(); index++) {
                var step = request.steps().get(index);
                steps.add(new ObligationStep(step.title().trim(), trimOptional(step.instructions()), index + 1));
            }
            var photos = new ArrayList<ObligationPhotoRequirement>();
            for (int index = 0; index < request.photoRequirements().size(); index++) {
                var photo = request.photoRequirements().get(index);
                photos.add(new ObligationPhotoRequirement(photo.title().trim(),
                        trimOptional(photo.description()), index + 1));
            }
            obligation = new Obligation(request.title(), request.description(), request.siteName(),
                    request.assignedOperatorId(), request.startsAt(), request.dueAt(),
                    actor.getCompanyId(), actor.getSiteId(), steps, photos);
        }
        return ObligationDetail.from(repository.saveAndFlush(obligation), clock.instant());
    }

    @Transactional(readOnly = true)
    public ObligationDetail find(Long id, User actor) {
        Obligation obligation = repository.findByIdAndCompanyIdAndSiteId(
                        id, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligación no encontrada"));
        if (actor.getRole() == Role.OPERATOR && !obligation.getAssignedOperatorId().equals(actor.getId())) {
            throw new AccessDeniedException("Forbidden");
        }
        return ObligationDetail.from(obligation, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<AssignedObligation> findForOperator(User actor) {
        return repository.findByAssignedOperatorId(actor.getId()).stream()
                .filter(obligation -> obligation.getCompanyId().equals(actor.getCompanyId())
                        && obligation.getSiteId().equals(actor.getSiteId()))
                .map(AssignedObligation::from).toList();
    }

    private static void validateExecutionDetails(CreateObligation request) {
        boolean hasDetails = request.startsAt() != null || request.steps() != null
                || request.photoRequirements() != null;
        if (!hasDetails) return;

        var errors = new LinkedHashMap<String, List<String>>();
        if (request.startsAt() == null) {
            errors.put("startsAt", List.of("es obligatorio al configurar la ejecución"));
        } else if (!request.startsAt().isBefore(request.dueAt())) {
            errors.put("dueAt", List.of("debe ser posterior a startsAt"));
        }
        if (request.steps() == null || request.steps().isEmpty()) {
            errors.put("steps", List.of("requiere al menos un paso al configurar la ejecución"));
        }
        if (request.photoRequirements() == null) {
            errors.put("photoRequirements", List.of("debe indicarse; use una lista vacía si no exige fotografías"));
        }
        if (!errors.isEmpty()) throw new FieldValidationException("Datos inválidos", errors);
    }

    private static String trimOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateObligation(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 255) String siteName,
            @NotNull @Positive Long assignedOperatorId,
            @NotNull Instant dueAt,
            Instant startsAt,
            List<@NotNull @Valid StepRequest> steps,
            List<@NotNull @Valid PhotoRequirementRequest> photoRequirements) {}

    public record StepRequest(@NotBlank @Size(max = 255) String title,
                              @Size(max = 2000) String instructions) {}

    public record PhotoRequirementRequest(@NotBlank @Size(max = 255) String title,
                                          @Size(max = 2000) String description) {}

    // Keep the existing list payload stable; execution details belong to the detail endpoint.
    public record AssignedObligation(Long id, Long companyId, Long siteId, String title,
                                     String description, String siteName, Long assignedOperatorId,
                                     ObligationStatus status, Instant dueAt) {
        static AssignedObligation from(Obligation obligation) {
            return new AssignedObligation(obligation.getId(), obligation.getCompanyId(), obligation.getSiteId(),
                    obligation.getTitle(), obligation.getDescription(), obligation.getSiteName(),
                    obligation.getAssignedOperatorId(), obligation.getStatus(), obligation.getDueAt());
        }
    }

    public record ObligationDetail(Long id, Long companyId, Long siteId, String title, String description,
                                   String siteName, Long assignedOperatorId, ObligationStatus status,
                                   Instant dueAt, Instant startsAt, String timezone,
                                   List<StepResponse> steps, List<PhotoRequirementResponse> photoRequirements) {
        static ObligationDetail from(Obligation obligation, Instant now) {
            return new ObligationDetail(obligation.getId(), obligation.getCompanyId(), obligation.getSiteId(),
                    obligation.getTitle(), obligation.getDescription(), obligation.getSiteName(),
                    obligation.getAssignedOperatorId(), obligation.statusAt(now), obligation.getDueAt(),
                    obligation.getStartsAt(), Obligation.TIMEZONE,
                    obligation.getSteps().stream().map(step -> new StepResponse(
                            step.getId(), step.getTitle(), step.getInstructions(), step.getPosition())).toList(),
                    obligation.getPhotoRequirements().stream().map(photo -> new PhotoRequirementResponse(
                            photo.getId(), photo.getTitle(), photo.getDescription(), photo.getPosition())).toList());
        }
    }

    public record StepResponse(Long id, String title, String instructions, int position) {}
    public record PhotoRequirementResponse(Long id, String title, String description, int position) {}
}
