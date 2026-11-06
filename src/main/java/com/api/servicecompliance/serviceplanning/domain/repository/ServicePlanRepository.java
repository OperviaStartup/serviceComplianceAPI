package com.api.servicecompliance.serviceplanning.domain.repository;

import com.api.servicecompliance.serviceplanning.domain.model.ServicePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicePlanRepository extends JpaRepository<ServicePlan, Long> {
    Optional<ServicePlan> findByIdAndCompanyId(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from ServicePlan plan where plan.id = :id and plan.companyId = :companyId")
    Optional<ServicePlan> lockByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    @Query("select (count(plan) > 0) from ServicePlan plan where plan.companyId = :companyId "
            + "and plan.serviceId = :serviceId and plan.siteId = :siteId "
            + "and plan.state = com.api.servicecompliance.serviceplanning.domain.model.ServicePlanState.ACTIVE "
            + "and plan.seriesId <> :seriesId and plan.startDate <= :endDate and plan.endDate >= :startDate")
    boolean existsActiveOverlap(@Param("companyId") Long companyId, @Param("serviceId") Long serviceId,
                                @Param("siteId") Long siteId, @Param("seriesId") UUID seriesId,
                                @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    boolean existsBySupersedesPlanIdAndState(Long supersedesPlanId,
                                             com.api.servicecompliance.serviceplanning.domain.model.ServicePlanState state);
}
