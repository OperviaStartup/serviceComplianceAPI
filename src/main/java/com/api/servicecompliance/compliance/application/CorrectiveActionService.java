package com.api.servicecompliance.compliance.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.compliance.domain.model.ComplianceCase;
import com.api.servicecompliance.compliance.domain.model.ComplianceOutcome;
import com.api.servicecompliance.compliance.domain.model.ComplianceResult;
import com.api.servicecompliance.compliance.domain.model.CorrectiveAction;
import com.api.servicecompliance.compliance.domain.model.CorrectiveActionEvent;
import com.api.servicecompliance.compliance.domain.model.CorrectiveActionState;
import com.api.servicecompliance.compliance.domain.model.CorrectiveActionStatus;
import com.api.servicecompliance.compliance.domain.repository.ComplianceCaseRepository;
import com.api.servicecompliance.compliance.domain.repository.ComplianceResultRepository;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveActionRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Validated
public class CorrectiveActionService {
    private final ComplianceCaseRepository cases;
    private final ComplianceResultRepository results;
    private final CorrectiveActionRepository actions;
    private final UserRepository users;
    private final Clock clock;

    public CorrectiveActionService(ComplianceCaseRepository cases,
                                   ComplianceResultRepository results,
                                   CorrectiveActionRepository actions,
                                   UserRepository users, Clock clock) {
        this.cases = cases;
        this.results = results;
        this.actions = actions;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public CorrectiveActionResponse create(Long caseId, @Valid CreateCorrectiveActionCommand command,
                                           User supervisor) {
        requireSupervisor(supervisor);
        ComplianceCase complianceCase = cases.lockInScope(
                        caseId, supervisor.getCompanyId(), supervisor.getSiteId())
                .orElseThrow(CorrectiveActionService::notFound);
        ComplianceResult latestResult = results
                .findFirstByComplianceCaseIdOrderByDecisionVersionDesc(caseId)
                .orElseThrow(() -> notEligible(complianceCase.getDecisionVersion()));
        if (latestResult.getOutcome() != ComplianceOutcome.NON_COMPLIANT) {
            throw notEligible(complianceCase.getDecisionVersion());
        }
        User assignee = users.findById(command.assigneeId())
                .orElseThrow(() -> new AccessDeniedException("Operator is not eligible"));
        requireEligibleAssignee(assignee, complianceCase);

        Instant now = clock.instant();
        CorrectiveAction action = actions.saveAndFlush(new CorrectiveAction(
                complianceCase.getId(), latestResult.getId(), complianceCase.getCompanyId(),
                complianceCase.getSiteId(), assignee.getId(), command.description().trim(),
                command.targetDate(), supervisor.getId(), now));
        return response(action, now);
    }

    @Transactional(readOnly = true)
    public CorrectiveActionResponse get(Long actionId, User actor) {
        requireActive(actor);
        CorrectiveAction action = actions.findByIdAndCompanyIdAndSiteId(
                        actionId, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(CorrectiveActionService::notFound);
        if (actor.getRole() == Role.OPERATOR && !Objects.equals(action.getAssigneeId(), actor.getId())) {
            throw new AccessDeniedException("Corrective action is assigned to another operator");
        }
        if (actor.getRole() != Role.OPERATOR && actor.getRole() != Role.SUPERVISOR) {
            throw new AccessDeniedException("Corrective action role required");
        }
        return response(action, clock.instant());
    }

    @Transactional
    public CorrectiveActionResponse submitAttention(Long actionId, @Valid TransitionCommand command,
                                                    User operator) {
        requireActive(operator);
        CorrectiveAction action = lockInActorScope(actionId, operator);
        if (operator.getRole() != Role.OPERATOR
                || !Objects.equals(action.getAssigneeId(), operator.getId())) {
            throw new AccessDeniedException("Assigned operator required");
        }
        verifyExpectedVersion(action, command.expectedVersion());
        transition(action, () -> action.submitAttention(
                operator.getId(), command.reason().trim(), clock.instant()));
        return response(actions.saveAndFlush(action), clock.instant());
    }

    @Transactional
    public CorrectiveActionResponse returnForCorrection(Long actionId, @Valid TransitionCommand command,
                                                        User supervisor) {
        requireSupervisor(supervisor);
        CorrectiveAction action = lockInActorScope(actionId, supervisor);
        verifyExpectedVersion(action, command.expectedVersion());
        transition(action, () -> action.returnForCorrection(
                supervisor.getId(), command.reason().trim(), clock.instant()));
        return response(actions.saveAndFlush(action), clock.instant());
    }

    @Transactional
    public CorrectiveActionResponse verifyAndClose(Long actionId, @Valid TransitionCommand command,
                                                   User supervisor) {
        requireSupervisor(supervisor);
        CorrectiveAction action = lockInActorScope(actionId, supervisor);
        verifyExpectedVersion(action, command.expectedVersion());
        transition(action, () -> action.verifyAndClose(
                supervisor.getId(), command.reason().trim(), clock.instant()));
        return response(actions.saveAndFlush(action), clock.instant());
    }

    private CorrectiveAction lockInActorScope(Long actionId, User actor) {
        return actions.lockInScope(actionId, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(CorrectiveActionService::notFound);
    }

    private static void transition(CorrectiveAction action, Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException exception) {
            throw new ApiConflictException("CORRECTIVE_ACTION_INVALID_STATE",
                    "La acción correctiva no admite esa transición", action.getVersion());
        }
    }

    private static void verifyExpectedVersion(CorrectiveAction action, long expectedVersion) {
        if (action.getVersion() != expectedVersion) {
            throw new ApiConflictException("VERSION_CONFLICT",
                    "La acción correctiva cambió desde la última lectura", action.getVersion());
        }
    }

    private static void requireSupervisor(User actor) {
        requireActive(actor);
        if (actor.getRole() != Role.SUPERVISOR) {
            throw new AccessDeniedException("Supervisor role required");
        }
    }

    private static void requireActive(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new AccessDeniedException("Active account required");
        }
    }

    private static void requireEligibleAssignee(User assignee, ComplianceCase complianceCase) {
        if (!assignee.isActive() || assignee.getRole() != Role.OPERATOR
                || !Objects.equals(assignee.getCompanyId(), complianceCase.getCompanyId())
                || !Objects.equals(assignee.getSiteId(), complianceCase.getSiteId())) {
            throw new AccessDeniedException("Operator is not eligible");
        }
    }

    private static ApiConflictException notEligible(long currentVersion) {
        return new ApiConflictException("CORRECTIVE_ACTION_NOT_ELIGIBLE",
                "El último resultado del caso debe ser NON_COMPLIANT", currentVersion);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Acción correctiva no encontrada");
    }

    static CorrectiveActionResponse response(CorrectiveAction action, Instant now) {
        List<CorrectiveActionEventResponse> events = action.getEvents().stream()
                .map(CorrectiveActionService::eventResponse).toList();
        return new CorrectiveActionResponse(action.getId(), action.getComplianceCaseId(),
                action.getSourceComplianceResultId(), action.getCompanyId(), action.getSiteId(),
                action.getAssigneeId(), action.getDescription(), action.getTargetDate(),
                action.getTimezone(), action.getState(), action.statusAt(now), action.getVersion(),
                action.getCreatedBy(), action.getCreatedAt(), action.getUpdatedBy(), action.getUpdatedAt(),
                action.getClosedBy(), action.getClosedAt(), events);
    }

    private static CorrectiveActionEventResponse eventResponse(CorrectiveActionEvent event) {
        return new CorrectiveActionEventResponse(event.getId(), event.getActionVersion(),
                event.getFromState(), event.getToState(), event.getActorId(),
                event.getReason(), event.getOccurredAt());
    }

    public record CreateCorrectiveActionCommand(
            @NotNull Long assigneeId,
            @NotBlank @Size(max = 4000) String description,
            @NotNull LocalDate targetDate) {}

    public record TransitionCommand(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 2000) String reason) {}

    public record CorrectiveActionResponse(
            Long id, Long caseId, Long sourceComplianceResultId, Long companyId, Long siteId,
            Long assigneeId, String description, LocalDate targetDate, String timezone,
            CorrectiveActionState state, CorrectiveActionStatus status, long version,
            Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt,
            Long closedBy, Instant closedAt, List<CorrectiveActionEventResponse> events) {}

    public record CorrectiveActionEventResponse(
            Long id, long actionVersion, CorrectiveActionState fromState,
            CorrectiveActionState toState, Long actorId, String reason, Instant occurredAt) {}
}
