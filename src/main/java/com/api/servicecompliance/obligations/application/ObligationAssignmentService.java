package com.api.servicecompliance.obligations.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.obligations.domain.model.*;
import com.api.servicecompliance.obligations.domain.repository.*;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.Role;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class ObligationAssignmentService {
    private final ObligationRepository obligations;
    private final ObligationAssignmentHistoryRepository history;
    private final UserRepository users;
    private final Clock clock;

    public ObligationAssignmentService(ObligationRepository obligations, ObligationAssignmentHistoryRepository history,
                                       UserRepository users, Clock clock) {
        this.obligations = obligations;
        this.history = history;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public AssignmentResponse assign(Long obligationId, AssignmentRequest request, User actor) {
        if (actor == null || !actor.isActive() || (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPERVISOR)) {
            throw new AccessDeniedException("Supervisor or administrator role required");
        }
        Obligation obligation = obligations.lockInScope(obligationId, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligación no encontrada"));
        if (obligation.getStatus() != ObligationStatus.ASSIGNED) {
            throw new ApiConflictException("OBLIGATION_NOT_ASSIGNABLE",
                    "Solo se puede asignar o reasignar una obligación sin iniciar");
        }
        User assignee = users.findById(request.operatorId())
                .orElseThrow(() -> new AccessDeniedException("Operator is not eligible"));
        if (!assignee.isActive() || assignee.getRole() != Role.OPERATOR
                || !actor.getCompanyId().equals(assignee.getCompanyId())
                || !actor.getSiteId().equals(assignee.getSiteId())) {
            throw new AccessDeniedException("Operator is not eligible");
        }
        Long previous = obligation.getAssignedOperatorId();
        if (request.operatorId().equals(previous)) {
            return AssignmentResponse.from(obligation);
        }
        obligation.assignTo(assignee.getId());
        obligations.saveAndFlush(obligation);
        history.save(new ObligationAssignmentHistory(obligation.getId(), previous, assignee.getId(),
                actor.getId(), clock.instant(), request.reason().trim()));
        return AssignmentResponse.from(obligation);
    }

    public record AssignmentRequest(@NotNull Long operatorId, @NotBlank @Size(max = 2000) String reason) {}
    public record AssignmentResponse(Long obligationId, Long operatorId, long assignmentVersion) {
        static AssignmentResponse from(Obligation obligation) {
            return new AssignmentResponse(obligation.getId(), obligation.getAssignedOperatorId(), obligation.getAssignmentVersion());
        }
    }
}
