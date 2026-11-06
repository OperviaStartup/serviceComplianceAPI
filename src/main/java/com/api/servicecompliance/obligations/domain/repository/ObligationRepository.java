package com.api.servicecompliance.obligations.domain.repository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObligationRepository extends JpaRepository<Obligation,Long> {
    List<Obligation> findByAssignedOperatorId(Long operatorId);
    Optional<Obligation> findByIdAndCompanyIdAndSiteId(Long id, Long companyId, Long siteId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Obligation o where o.id = :id and o.companyId = :company and o.siteId = :site")
    Optional<Obligation> lockInScope(@Param("id") Long id, @Param("company") Long company,
                                   @Param("site") Long site);
    @Modifying
    @Query("update Obligation o set o.status = com.api.servicecompliance.obligations.domain.model.ObligationStatus.OVERDUE "
            + "where o.startsAt is not null and o.dueAt <= :now and o.status in "
            + "(com.api.servicecompliance.obligations.domain.model.ObligationStatus.ASSIGNED, "
            + "com.api.servicecompliance.obligations.domain.model.ObligationStatus.IN_PROGRESS)")
    int expireFieldObligations(@Param("now") Instant now);
}
