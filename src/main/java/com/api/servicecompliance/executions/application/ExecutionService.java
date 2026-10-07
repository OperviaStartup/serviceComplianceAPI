package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.executions.domain.model.Execution;
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
    public Execution register(RegisterExecution request,Long operatorId){
        var obligation=obligationRepository.findById(request.obligationId()).orElseThrow(()->new DomainException("Obligación no encontrada"));
        if(!obligation.getAssignedOperatorId().equals(operatorId)) throw new DomainException("La obligación no está asignada al operario autenticado");
        return repository.save(new Execution(request.obligationId(),operatorId,request.result(),request.notes()));
    }
    public Execution find(Long id){return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Ejecución no encontrada"));}
    public record RegisterExecution(@NotNull Long obligationId,
                                    @NotNull com.api.servicecompliance.executions.domain.model.ExecutionResult result,
                                    @Size(max=2000) String notes){}
}
