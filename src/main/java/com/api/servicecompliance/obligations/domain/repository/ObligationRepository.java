package com.api.servicecompliance.obligations.domain.repository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import com.api.servicecompliance.obligations.domain.model.ObligationStatus;
import java.util.Set;

public interface ObligationRepository extends JpaRepository<Obligation,Long> {
    List<Obligation> findByAssignedOperatorId(Long operatorId);
    Optional<Obligation> findBySourceDefinitionIdAndOccurrenceDate(Long definitionId, LocalDate occurrenceDate);
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

    @Query("""
            select obligation from Obligation obligation
            where obligation.companyId = :companyId
              and obligation.siteId = :siteId
              and obligation.assignedOperatorId = :operatorId
              and coalesce(obligation.startsAt, obligation.dueAt) >= :fromInclusive
              and coalesce(obligation.startsAt, obligation.dueAt) < :toExclusive
              and (
                    (:includeOverdue = true and (
                        obligation.status = com.api.servicecompliance.obligations.domain.model.ObligationStatus.OVERDUE
                        or (obligation.startsAt is not null
                            and obligation.status <> com.api.servicecompliance.obligations.domain.model.ObligationStatus.SUBMITTED
                            and obligation.dueAt <= :now)
                    ))
                    or (
                        obligation.status in :statuses
                        and obligation.status <> com.api.servicecompliance.obligations.domain.model.ObligationStatus.OVERDUE
                        and (obligation.startsAt is null
                            or obligation.status = com.api.servicecompliance.obligations.domain.model.ObligationStatus.SUBMITTED
                            or obligation.dueAt > :now)
                    )
              )
            order by coalesce(obligation.startsAt, obligation.dueAt), obligation.id
            """)
    List<Obligation> findAssignedPage(@Param("companyId") Long companyId,
                                      @Param("siteId") Long siteId,
                                      @Param("operatorId") Long operatorId,
                                      @Param("fromInclusive") Instant fromInclusive,
                                      @Param("toExclusive") Instant toExclusive,
                                      @Param("statuses") Set<ObligationStatus> statuses,
                                      @Param("includeOverdue") boolean includeOverdue,
                                      @Param("now") Instant now,
                                      Pageable pageable);

    @Query("""
            select obligation from Obligation obligation
            where obligation.companyId = :companyId
              and obligation.siteId = :siteId
              and obligation.assignedOperatorId = :operatorId
              and coalesce(obligation.startsAt, obligation.dueAt) >= :fromInclusive
              and coalesce(obligation.startsAt, obligation.dueAt) < :toExclusive
              and (
                    (:includeOverdue = true and (
                        obligation.status = com.api.servicecompliance.obligations.domain.model.ObligationStatus.OVERDUE
                        or (obligation.startsAt is not null
                            and obligation.status <> com.api.servicecompliance.obligations.domain.model.ObligationStatus.SUBMITTED
                            and obligation.dueAt <= :now)
                    ))
                    or (
                        obligation.status in :statuses
                        and obligation.status <> com.api.servicecompliance.obligations.domain.model.ObligationStatus.OVERDUE
                        and (obligation.startsAt is null
                            or obligation.status = com.api.servicecompliance.obligations.domain.model.ObligationStatus.SUBMITTED
                            or obligation.dueAt > :now)
                    )
              )
              and (coalesce(obligation.startsAt, obligation.dueAt) > :cursorTime
                   or (coalesce(obligation.startsAt, obligation.dueAt) = :cursorTime
                       and obligation.id > :cursorId))
            order by coalesce(obligation.startsAt, obligation.dueAt), obligation.id
            """)
    List<Obligation> findAssignedPageAfter(@Param("companyId") Long companyId,
                                           @Param("siteId") Long siteId,
                                           @Param("operatorId") Long operatorId,
                                           @Param("fromInclusive") Instant fromInclusive,
                                           @Param("toExclusive") Instant toExclusive,
                                           @Param("statuses") Set<ObligationStatus> statuses,
                                           @Param("includeOverdue") boolean includeOverdue,
                                           @Param("now") Instant now,
                                           @Param("cursorTime") Instant cursorTime,
                                           @Param("cursorId") Long cursorId,
                                           Pageable pageable);
}
