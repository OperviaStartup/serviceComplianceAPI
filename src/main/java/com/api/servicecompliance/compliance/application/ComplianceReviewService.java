package com.api.servicecompliance.compliance.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.executions.domain.model.Evidence;
import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.model.ExecutionState;
import com.api.servicecompliance.executions.domain.model.ExecutionStepProgress;
import com.api.servicecompliance.executions.domain.model.OperationalException;
import com.api.servicecompliance.executions.domain.model.OperationalExceptionEvidence;
import com.api.servicecompliance.executions.domain.repository.EvidenceRepository;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.executions.domain.repository.ExecutionStepProgressRepository;
import com.api.servicecompliance.executions.domain.repository.OperationalExceptionEvidenceRepository;
import com.api.servicecompliance.executions.domain.repository.OperationalExceptionRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.model.ObligationEvidenceRequirement;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ComplianceReviewService {
    private final ExecutionRepository executions;
    private final ObligationRepository obligations;
    private final ExecutionStepProgressRepository stepProgress;
    private final EvidenceRepository evidence;
    private final OperationalExceptionRepository exceptions;
    private final OperationalExceptionEvidenceRepository exceptionEvidence;
    private final UserRepository users;
    private final Clock clock;

    public ComplianceReviewService(ExecutionRepository executions, ObligationRepository obligations,
                                   ExecutionStepProgressRepository stepProgress, EvidenceRepository evidence,
                                   OperationalExceptionRepository exceptions,
                                   OperationalExceptionEvidenceRepository exceptionEvidence,
                                   UserRepository users, Clock clock) {
        this.executions = executions;
        this.obligations = obligations;
        this.stepProgress = stepProgress;
        this.evidence = evidence;
        this.exceptions = exceptions;
        this.exceptionEvidence = exceptionEvidence;
        this.users = users;
        this.clock = clock;
    }

    public ComplianceReview review(Long executionId, User reviewer) {
        requireSupervisor(reviewer);
        Execution execution = executions.findById(executionId)
                .orElseThrow(ComplianceReviewService::notFound);
        Obligation obligation = obligations.findByIdAndCompanyIdAndSiteId(
                        execution.getObligationId(), reviewer.getCompanyId(), reviewer.getSiteId())
                .orElseThrow(ComplianceReviewService::notFound);
        if (execution.getState() != ExecutionState.SUBMITTED) {
            throw new ApiConflictException("REVIEW_NOT_READY",
                    "La ejecución debe estar enviada antes de iniciar la revisión", execution.getVersion());
        }

        List<ExecutionStepProgress> storedProgress = stepProgress.findByExecutionId(executionId);
        List<Evidence> storedEvidence = evidence.findByExecutionIdOrderByCreatedAtAscIdAsc(executionId);
        List<OperationalException> storedExceptions =
                exceptions.findByExecutionIdOrderByReportedAtAscIdAsc(executionId);
        List<OperationalExceptionEvidence> storedExceptionEvidence = storedExceptions.isEmpty()
                ? List.of()
                : exceptionEvidence.findByExceptionIdIn(storedExceptions.stream()
                        .map(OperationalException::getId).toList());

        Map<Long, Actor> actors = actors(reviewer.getCompanyId(), actorIds(
                execution, reviewer, storedEvidence, storedExceptions));
        Map<Long, ExecutionStepProgress> progressByStep = storedProgress.stream()
                .collect(Collectors.toMap(ExecutionStepProgress::getStepId, Function.identity()));
        Map<Long, List<Evidence>> evidenceByRequirement = storedEvidence.stream()
                .filter(item -> item.getEvidenceRequirementId() != null)
                .collect(Collectors.groupingBy(Evidence::getEvidenceRequirementId));
        Map<Long, List<Evidence>> evidenceByPhotoRequirement = storedEvidence.stream()
                .filter(item -> item.getPhotoRequirementId() != null)
                .collect(Collectors.groupingBy(Evidence::getPhotoRequirementId));
        Map<Long, List<Long>> evidenceByException = storedExceptionEvidence.stream()
                .collect(Collectors.groupingBy(OperationalExceptionEvidence::getExceptionId,
                        Collectors.mapping(OperationalExceptionEvidence::getEvidenceId,
                                Collectors.collectingAndThen(Collectors.toList(), values ->
                                        values.stream().sorted().toList()))));

        List<StepReview> steps = obligation.getSteps().stream()
                .map(step -> {
                    ExecutionStepProgress progress = progressByStep.get(step.getId());
                    return new StepReview(step.getId(), step.getTitle(), step.getInstructions(), step.getPosition(),
                            progress != null && progress.isCompleted(), progress == null ? null : progress.getUpdatedAt());
                }).toList();
        List<PhotoRequirementReview> photoRequirements = obligation.getPhotoRequirements().stream()
                .map(requirement -> new PhotoRequirementReview(requirement.getId(), requirement.getTitle(),
                        requirement.getDescription(), requirement.getPosition(),
                        evidenceByPhotoRequirement.getOrDefault(requirement.getId(), List.of()).stream()
                                .map(Evidence::getId).toList()))
                .toList();
        List<EvidenceRequirementReview> evidenceRequirements = obligation.getEvidenceRequirements().stream()
                .map(requirement -> evidenceRequirement(requirement,
                        evidenceByRequirement.getOrDefault(requirement.getId(), List.of())))
                .toList();
        List<MissingInformation> missing = missingInformation(steps, photoRequirements, evidenceRequirements);

        return new ComplianceReview(
                "execution-" + execution.getId() + "-v" + execution.getVersion(), clock.instant(),
                actors.get(reviewer.getId()),
                new ObligationSnapshot(obligation.getId(), obligation.getTitle(), obligation.getDescription(),
                        obligation.getSiteId(), obligation.getSiteName(), obligation.getAssignedOperatorId(),
                        obligation.getStartsAt(), obligation.getDueAt(), obligation.getTimezone(),
                        obligation.getOccurrenceDate(), obligation.getSourcePlanId(), obligation.getSourcePlanVersion(),
                        obligation.getSourceDefinitionId(), obligation.getSourceDefinitionVersion(),
                        steps, photoRequirements, evidenceRequirements),
                new ExecutionSnapshot(execution.getId(), actors.get(execution.getOperatorId()),
                        execution.getState(), execution.getResult(), execution.getNotes(), execution.getStartedAt(),
                        execution.getSubmittedAt(), execution.getUpdatedAt(), execution.getVersion()),
                storedEvidence.stream().map(item -> evidence(item, actors.get(item.getCreatedBy()))).toList(),
                storedExceptions.stream().map(item -> new ExceptionReview(item.getId(), item.getStepId(),
                        item.getReason().name(), item.getDescription(), actors.get(item.getReportedBy()),
                        item.getReportedAt(), item.getOperationId().toString(),
                        evidenceByException.getOrDefault(item.getId(), List.of()))).toList(),
                missing.isEmpty(), missing);
    }

    private Map<Long, Actor> actors(Long companyId, Set<Long> actorIds) {
        return users.findAllById(actorIds).stream()
                .filter(user -> Objects.equals(user.getCompanyId(), companyId))
                .collect(Collectors.toMap(User::getId, user -> new Actor(user.getId(), user.getFullName(),
                        user.getRole(), user.isActive())));
    }

    private static Set<Long> actorIds(Execution execution, User reviewer, Collection<Evidence> evidence,
                                      Collection<OperationalException> exceptions) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(execution.getOperatorId());
        ids.add(reviewer.getId());
        evidence.stream().map(Evidence::getCreatedBy).filter(Objects::nonNull).forEach(ids::add);
        exceptions.stream().map(OperationalException::getReportedBy).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    private static EvidenceRequirementReview evidenceRequirement(ObligationEvidenceRequirement requirement,
                                                                  List<Evidence> storedEvidence) {
        return new EvidenceRequirementReview(requirement.getId(), requirement.getSourceRequirementId(),
                requirement.getEvidenceType().name(), requirement.isRequired(), requirement.getMinimumCount(),
                requirement.getMaximumCount(), requirement.getPosition(), requirement.getConstraints(),
                storedEvidence.size(), storedEvidence.stream().map(Evidence::getId).toList());
    }

    private static List<MissingInformation> missingInformation(List<StepReview> steps,
                                                               List<PhotoRequirementReview> photos,
                                                               List<EvidenceRequirementReview> requirements) {
        List<MissingInformation> missing = new ArrayList<>();
        steps.stream().filter(step -> !step.completed()).forEach(step -> missing.add(
                new MissingInformation("STEP", step.id(), "STEP_NOT_COMPLETED", 1, 0)));
        photos.stream().filter(photo -> photo.evidenceIds().isEmpty()).forEach(photo -> missing.add(
                new MissingInformation("PHOTO_REQUIREMENT", photo.id(), "PHOTO_REQUIRED", 1, 0)));
        requirements.stream().filter(EvidenceRequirementReview::required)
                .filter(requirement -> requirement.actualCount() < requirement.minimumCount())
                .forEach(requirement -> missing.add(new MissingInformation("EVIDENCE_REQUIREMENT",
                        requirement.id(), "MINIMUM_EVIDENCE_NOT_MET", requirement.minimumCount(),
                        requirement.actualCount())));
        return missing.stream().sorted(Comparator.comparing(MissingInformation::category)
                .thenComparing(MissingInformation::requirementId)).toList();
    }

    private static EvidenceReview evidence(Evidence value, Actor actor) {
        String contentPath = value.getStorageKey() == null ? null
                : "/api/v1/evidence/" + value.getId() + "/content";
        return new EvidenceReview(value.getId(), value.getEvidenceRequirementId(), value.getPhotoRequirementId(),
                value.getType().name(), actor, value.getTextValue(), value.getLatitude(), value.getLongitude(),
                value.getAccuracyMeters(), value.getContentType(), value.getFileSize(), value.getChecksum(),
                value.getCapturedAt(), value.getCreatedAt(), contentPath);
    }

    private static void requireSupervisor(User reviewer) {
        if (reviewer == null || !reviewer.isActive() || reviewer.getRole() != Role.SUPERVISOR) {
            throw new AccessDeniedException("Supervisor role required");
        }
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Revisión de cumplimiento no encontrada");
    }

    public record ComplianceReview(String reviewVersion, Instant reviewedAt, Actor reviewedBy,
                                   ObligationSnapshot obligation, ExecutionSnapshot execution,
                                   List<EvidenceReview> evidence, List<ExceptionReview> exceptions,
                                   boolean complete, List<MissingInformation> missingInformation) {}

    public record Actor(Long id, String fullName, Role role, boolean active) {}

    public record ObligationSnapshot(Long id, String title, String description, Long siteId, String siteName,
                                     Long assignedOperatorId, Instant startsAt, Instant dueAt, String timezone,
                                     LocalDate occurrenceDate, Long sourcePlanId, Integer sourcePlanVersion,
                                     Long sourceDefinitionId, Long sourceDefinitionVersion,
                                     List<StepReview> steps, List<PhotoRequirementReview> photoRequirements,
                                     List<EvidenceRequirementReview> evidenceRequirements) {}

    public record ExecutionSnapshot(Long id, Actor operator, ExecutionState state,
                                    com.api.servicecompliance.executions.domain.model.ExecutionResult result,
                                    String notes, Instant startedAt, Instant submittedAt, Instant updatedAt,
                                    long version) {}

    public record StepReview(Long id, String title, String instructions, int position,
                             boolean completed, Instant completedAt) {}

    public record PhotoRequirementReview(Long id, String title, String description, int position,
                                         List<Long> evidenceIds) {}

    public record EvidenceRequirementReview(Long id, Long sourceRequirementId, String type, boolean required,
                                            int minimumCount, int maximumCount, int position,
                                            Map<String, String> constraints, int actualCount,
                                            List<Long> evidenceIds) {}

    public record EvidenceReview(Long id, Long evidenceRequirementId, Long photoRequirementId, String type,
                                 Actor createdBy, String textValue, BigDecimal latitude, BigDecimal longitude,
                                 BigDecimal accuracyMeters, String contentType, Long fileSize, String checksum,
                                 Instant capturedAt, Instant createdAt, String contentPath) {}

    public record ExceptionReview(Long id, Long stepId, String reason, String description, Actor reportedBy,
                                  Instant reportedAt, String operationId, List<Long> evidenceIds) {}

    public record MissingInformation(String category, Long requirementId, String code,
                                     int expectedCount, int actualCount) {}
}
