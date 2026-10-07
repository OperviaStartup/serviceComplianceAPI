package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import org.springframework.stereotype.Service;

@Service public class ExecutionService {
    private final ExecutionRepository repository;
    public ExecutionService(ExecutionRepository repository){this.repository=repository;}
    public Execution register(RegisterExecution request,Long operatorId){return repository.save(new Execution(request.obligationId(),operatorId,request.result(),request.notes()));}
    public Execution find(Long id){return repository.findById(id).orElseThrow(()->new IllegalArgumentException("Ejecución no encontrada"));}
    public record RegisterExecution(Long obligationId,com.api.servicecompliance.executions.domain.model.ExecutionResult result,String notes){}
}
