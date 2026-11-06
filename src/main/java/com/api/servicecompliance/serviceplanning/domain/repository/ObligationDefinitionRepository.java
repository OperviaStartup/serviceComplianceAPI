package com.api.servicecompliance.serviceplanning.domain.repository;

import com.api.servicecompliance.serviceplanning.domain.model.ObligationDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.util.List;

public interface ObligationDefinitionRepository extends JpaRepository<ObligationDefinition, Long> {
    @EntityGraph(attributePaths = {"evidenceRequirements", "evidenceRequirements.constraints"})
    Optional<ObligationDefinition> findByIdAndServicePlanId(Long id, Long servicePlanId);
    boolean existsByServicePlanId(Long servicePlanId);

    @EntityGraph(attributePaths = {"daysOfWeek", "evidenceRequirements", "evidenceRequirements.constraints"})
    List<ObligationDefinition> findAllByServicePlanId(Long servicePlanId);
}
