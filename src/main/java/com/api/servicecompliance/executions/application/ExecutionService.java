package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.shared.domain.Role;
import org.springframework.security.access.AccessDeniedException;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.DomainException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Service public class ExecutionService {
    private final ExecutionRepository repository;
    private final ObligationRepository obligationRepository;
    public ExecutionService(ExecutionRepository repository, ObligationRepository obligationRepository){this.repository=repository;this.obligationRepository=obligationRepository;}
    public Execution register(RegisterExecution request,User actor){
        var obligation=obligationRepository.findById(request.obligationId()).orElseThrow(()->new DomainException("Obligación no encontrada"));
        if(actor.getRole()!=Role.OPERATOR || !obligation.getCompanyId().equals(actor.getCompanyId()) || !obligation.getSiteId().equals(actor.getSiteId()) || !obligation.getAssignedOperatorId().equals(actor.getId())) throw new AccessDeniedException("Forbidden");
        return repository.save(new Execution(request.obligationId(),actor.getId(),request.result(),request.notes()));
    }
    public Execution find(Long id, User actor){
        Execution execution=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Ejecución no encontrada"));
        var obligation=obligationRepository.findById(execution.getObligationId()).orElseThrow(()->new ResourceNotFoundException("Ejecución no encontrada"));
        if(!obligation.getCompanyId().equals(actor.getCompanyId()) || !obligation.getSiteId().equals(actor.getSiteId()) || (actor.getRole()==Role.OPERATOR && !execution.getOperatorId().equals(actor.getId()))) throw new AccessDeniedException("Forbidden");
        return execution;
    }
    public record RegisterExecution(@NotNull Long obligationId,
                                    @NotNull com.api.servicecompliance.executions.domain.model.ExecutionResult result,
                                    @Size(max=2000) String notes){}
}
