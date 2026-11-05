package com.api.servicecompliance.serviceplanning.domain.repository;

import com.api.servicecompliance.serviceplanning.domain.model.ServicePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServicePlanRepository extends JpaRepository<ServicePlan, Long> {
    Optional<ServicePlan> findByIdAndCompanyId(Long id, Long companyId);
}
