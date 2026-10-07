package com.api.servicecompliance.obligations.application;

import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Service public class ObligationService {
    private final ObligationRepository repository;
    public ObligationService(ObligationRepository repository){this.repository=repository;}
    public Obligation create(CreateObligation request){return repository.save(new Obligation(request.title(),request.description(),request.siteName(),request.assignedOperatorId(),request.dueAt()));}
    public List<Obligation> findForOperator(Long operatorId){return repository.findByAssignedOperatorId(operatorId);}
    public record CreateObligation(@NotBlank @Size(max=255) String title,
                                   @NotBlank @Size(max=1000) String description,
                                   @NotBlank @Size(max=255) String siteName,
                                   @NotNull Long assignedOperatorId,
                                   @NotNull Instant dueAt){}
}
