package com.api.servicecompliance.compliance.domain.repository;

import com.api.servicecompliance.compliance.domain.model.CorrectiveAction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Long> {
    Optional<CorrectiveAction> findByIdAndCompanyIdAndSiteId(Long id, Long companyId, Long siteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select action from CorrectiveAction action where action.id = :id "
            + "and action.companyId = :companyId and action.siteId = :siteId")
    Optional<CorrectiveAction> lockInScope(@Param("id") Long id,
                                           @Param("companyId") Long companyId,
                                           @Param("siteId") Long siteId);
}
