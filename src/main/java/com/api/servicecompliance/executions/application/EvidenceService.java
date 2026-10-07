package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.executions.domain.model.Evidence;
import com.api.servicecompliance.executions.domain.repository.EvidenceRepository;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Service public class EvidenceService {
    private final EvidenceRepository evidenceRepository; private final ExecutionRepository executionRepository;
    public EvidenceService(EvidenceRepository evidenceRepository,ExecutionRepository executionRepository){this.evidenceRepository=evidenceRepository;this.executionRepository=executionRepository;}
    public Evidence add(Long executionId, EvidenceRequest request, Long operatorId){
        var execution=executionRepository.findById(executionId).orElseThrow(()->new DomainException("Ejecución no encontrada"));
        if(!execution.getOperatorId().equals(operatorId)) throw new DomainException("La ejecución no pertenece al operario autenticado");
        return evidenceRepository.save(new Evidence(executionId,request.type(),request.url(),request.description()));
    }
    public List<Evidence> findByExecution(Long executionId){return evidenceRepository.findByExecutionId(executionId);}
    public record EvidenceRequest(@NotNull com.api.servicecompliance.executions.domain.model.EvidenceType type,
                                  @NotBlank @Size(max=1000) String url,
                                  @Size(max=1000) String description){}
}
