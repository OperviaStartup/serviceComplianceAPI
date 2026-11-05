package com.api.servicecompliance.serviceplanning.domain.repository;

import com.api.servicecompliance.serviceplanning.domain.model.CompanyService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyServiceRepository extends JpaRepository<CompanyService, Long> {
    boolean existsByIdAndCompanyIdAndActiveTrue(Long id, Long companyId);
}
