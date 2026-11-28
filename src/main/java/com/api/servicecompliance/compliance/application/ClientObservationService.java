package com.api.servicecompliance.compliance.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.compliance.domain.model.ClientObservation;
import com.api.servicecompliance.compliance.domain.model.ClientObservationOrigin;
import com.api.servicecompliance.compliance.domain.model.ComplianceCase;
import com.api.servicecompliance.compliance.domain.repository.ClientObservationRepository;
import com.api.servicecompliance.compliance.domain.repository.ComplianceCaseRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ClientObservationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private final ComplianceCaseRepository cases;
    private final ClientObservationRepository observations;
    private final ComplianceDecisionService decisions;
    private final Clock clock;

    public ClientObservationService(ComplianceCaseRepository cases,
                                    ClientObservationRepository observations,
                                    ComplianceDecisionService decisions, Clock clock) {
        this.cases = cases;
        this.observations = observations;
        this.decisions = decisions;
        this.clock = clock;
    }

    @Transactional
    public ObservationResponse create(Long caseId, CreateObservationCommand command, User actor) {
        requireSupervisor(actor);
        ComplianceCase complianceCase = scopedCase(caseId, actor);
        validateObservedDate(command.observedDate());
        ClientObservation observation = observations.saveAndFlush(new ClientObservation(
                complianceCase.getId(), UUID.randomUUID(), 1, null, command.description().trim(),
                command.origin(), command.observedDate(), null, actor.getId(), clock.instant()));
        return response(observation);
    }

    @Transactional
    public ObservationResponse amend(Long observationId, AmendmentCommand command, User actor) {
        requireSupervisor(actor);
        ClientObservation requested = scopedObservation(observationId, actor);
        observations.findFirstBySeriesIdOrderByRevisionAsc(requested.getSeriesId())
                .orElseThrow(ClientObservationService::notFound);
        ClientObservation latest = observations
                .findFirstBySeriesIdOrderByRevisionDesc(requested.getSeriesId())
                .orElseThrow(ClientObservationService::notFound);
        if (!latest.getId().equals(requested.getId())) {
            throw new ApiConflictException("OBSERVATION_NOT_LATEST",
                    "Solo la última revisión de una observación puede enmendarse",
                    (long) latest.getRevision());
        }
        if (command.expectedRevision() != latest.getRevision()) {
            throw new ApiConflictException("VERSION_CONFLICT",
                    "La observación cambió desde la última lectura", (long) latest.getRevision());
        }
        validateObservedDate(command.observedDate());
        ClientObservation amendment = observations.saveAndFlush(new ClientObservation(
                latest.getComplianceCaseId(), latest.getSeriesId(), latest.getRevision() + 1,
                latest.getId(), command.description().trim(), command.origin(), command.observedDate(),
                command.reason().trim(), actor.getId(), clock.instant()));
        return response(amendment);
    }

    @Transactional(readOnly = true)
    public List<ObservationResponse> list(Long caseId, User actor) {
        requireSupervisor(actor);
        cases.findByIdAndCompanyIdAndSiteId(caseId, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(ClientObservationService::notFound);
        return observations.findAllByComplianceCaseIdOrderByRecordedAtAscIdAsc(caseId).stream()
                .map(ClientObservationService::response).toList();
    }

    @Transactional
    public ComplianceDecisionService.ComplianceResultResponse reevaluate(
            Long observationId, ComplianceDecisionService.ObservationReevaluationCommand command,
            User actor) {
        requireSupervisor(actor);
        return decisions.reevaluate(scopedObservation(observationId, actor), command, actor);
    }

    private ComplianceCase scopedCase(Long caseId, User actor) {
        return cases.lockInScope(caseId, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(ClientObservationService::notFound);
    }

    private ClientObservation scopedObservation(Long observationId, User actor) {
        return observations.findInScope(observationId, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(ClientObservationService::notFound);
    }

    private void validateObservedDate(LocalDate observedDate) {
        if (observedDate.isAfter(LocalDate.now(clock.withZone(BUSINESS_ZONE)))) {
            throw new FieldValidationException("La observación no es válida",
                    Map.of("observedDate", List.of("No puede estar en el futuro")));
        }
    }

    private static void requireSupervisor(User actor) {
        if (actor == null || !actor.isActive() || actor.getRole() != Role.SUPERVISOR) {
            throw new AccessDeniedException("Active supervisor required");
        }
    }

    private static ObservationResponse response(ClientObservation observation) {
        return new ObservationResponse(observation.getId(), observation.getComplianceCaseId(),
                observation.getSeriesId(), observation.getRevision(),
                observation.getSupersedesObservationId(), observation.getDescription(),
                observation.getOrigin(), observation.getObservedDate(), observation.getAmendmentReason(),
                observation.getRecordedBy(), observation.getRecordedAt());
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Observación de cliente no encontrada");
    }

    public record CreateObservationCommand(
            @NotBlank @Size(max = 4000) String description,
            @NotNull ClientObservationOrigin origin,
            @NotNull LocalDate observedDate) {}

    public record AmendmentCommand(
            @NotNull @Positive Integer expectedRevision,
            @NotBlank @Size(max = 4000) String description,
            @NotNull ClientObservationOrigin origin,
            @NotNull LocalDate observedDate,
            @NotBlank @Size(max = 1000) String reason) {}

    public record ObservationResponse(
            Long id, Long caseId, UUID seriesId, int revision, Long supersedesObservationId,
            String description, ClientObservationOrigin origin, LocalDate observedDate,
            String amendmentReason, Long recordedBy, Instant recordedAt) {}
}
