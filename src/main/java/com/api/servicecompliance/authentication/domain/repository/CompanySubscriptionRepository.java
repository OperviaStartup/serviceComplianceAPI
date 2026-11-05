package com.api.servicecompliance.authentication.domain.repository;

import com.api.servicecompliance.authentication.domain.model.CompanySubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from CompanySubscription subscription where subscription.companyId = :companyId")
    Optional<CompanySubscription> lockByCompanyId(@Param("companyId") Long companyId);
}
