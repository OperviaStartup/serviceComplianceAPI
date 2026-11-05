package com.api.servicecompliance.authentication.domain.repository;

import com.api.servicecompliance.authentication.domain.model.SiteScope;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteScopeRepository extends JpaRepository<SiteScope, Long> {
    boolean existsByIdAndCompanyId(Long id, Long companyId);
}
