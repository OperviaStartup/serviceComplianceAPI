package com.api.servicecompliance.compliance.domain.repository;

import com.api.servicecompliance.compliance.domain.model.ComplianceCase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ComplianceCaseRepository extends JpaRepository<ComplianceCase, Long> {
    Optional<ComplianceCase> findByObligationId(Long obligationId);

    Optional<ComplianceCase> findByIdAndCompanyIdAndSiteId(Long id, Long companyId, Long siteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select complianceCase from ComplianceCase complianceCase where complianceCase.id = :id "
            + "and complianceCase.companyId = :companyId and complianceCase.siteId = :siteId")
    Optional<ComplianceCase> lockInScope(@Param("id") Long id,
                                         @Param("companyId") Long companyId,
                                         @Param("siteId") Long siteId);
}
