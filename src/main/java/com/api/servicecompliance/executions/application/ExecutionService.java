package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.DomainException;
import org.springframework.stereotype.Service;

@Service public class ExecutionService {
    private final ExecutionRepository repository;
    private final ObligationRepository obligationRepository;
    public ExecutionService(ExecutionRepository repository, ObligationRepository obligationRepository){this.repository=repository;this.obligationRepository=obligationRepository;}
    public Execution register(RegisterExecution request,Long operatorId){
        var obligation=obligationRepository.findById(request.obligationId()).orElseThrow(()->new DomainException("Obligación no encontrada"));
        if(!obligation.getAssignedOperatorId().equals(operatorId)) throw new DomainException("La obligación no está asignada al operario autenticado");
        return repository.save(new Execution(request.obligationId(),operatorId,request.result(),request.notes()));
    }
    public Execution find(Long id){return repository.findById(id).orElseThrow(()->new IllegalArgumentException("Ejecución no encontrada"));}
    public record RegisterExecution(Long obligationId,com.api.servicecompliance.executions.domain.model.ExecutionResult result,String notes){}
}
