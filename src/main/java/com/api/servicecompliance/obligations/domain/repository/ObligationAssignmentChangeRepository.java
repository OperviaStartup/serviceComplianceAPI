package com.api.servicecompliance.obligations.domain.repository;

import com.api.servicecompliance.obligations.domain.model.ObligationAssignmentChange;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ObligationAssignmentChangeRepository extends JpaRepository<ObligationAssignmentChange, Long> {
    @Query("""
            select coalesce(max(change.id), 0)
            from ObligationAssignmentChange change
            where change.companyId = :companyId
              and change.siteId = :siteId
              and change.operatorId = :operatorId
            """)
    long findHighWatermark(@Param("companyId") Long companyId,
                           @Param("siteId") Long siteId,
                           @Param("operatorId") Long operatorId);

    List<ObligationAssignmentChange> findByCompanyIdAndSiteIdAndOperatorIdAndIdGreaterThanOrderByIdAsc(
            Long companyId, Long siteId, Long operatorId, Long id, Pageable pageable);
}
