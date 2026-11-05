package com.api.servicecompliance.obligations.application;

import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.shared.domain.Role;
import org.springframework.security.access.AccessDeniedException;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Service public class ObligationService {
    private final ObligationRepository repository;
    private final UserRepository users;
    public ObligationService(ObligationRepository repository, UserRepository users){this.repository=repository;this.users=users;}
    public Obligation create(CreateObligation request, User actor){
        User assignee=users.findById(request.assignedOperatorId()).orElseThrow(() -> new AccessDeniedException("Forbidden"));
        if (!assignee.isActive() || assignee.getRole()!=Role.OPERATOR || !actor.getCompanyId().equals(assignee.getCompanyId()) || !actor.getSiteId().equals(assignee.getSiteId())) throw new AccessDeniedException("Forbidden");
        Obligation obligation=new Obligation(request.title(),request.description(),request.siteName(),request.assignedOperatorId(),request.dueAt(),actor.getCompanyId(),actor.getSiteId());
        return repository.save(obligation);
    }
    public List<Obligation> findForOperator(User actor){return repository.findByAssignedOperatorId(actor.getId()).stream().filter(o -> o.getCompanyId().equals(actor.getCompanyId()) && o.getSiteId().equals(actor.getSiteId())).toList();}
    public record CreateObligation(@NotBlank @Size(max=255) String title,
                                   @NotBlank @Size(max=1000) String description,
                                   @NotBlank @Size(max=255) String siteName,
                                   @NotNull Long assignedOperatorId,
                                   @NotNull Instant dueAt){}
}
