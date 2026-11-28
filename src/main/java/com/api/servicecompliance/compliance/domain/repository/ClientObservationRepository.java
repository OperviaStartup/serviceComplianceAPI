package com.api.servicecompliance.compliance.domain.repository;

import com.api.servicecompliance.compliance.domain.model.ClientObservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientObservationRepository extends JpaRepository<ClientObservation, Long> {
    @Query("select observation from ClientObservation observation, ComplianceCase complianceCase "
            + "where observation.id = :id and observation.complianceCaseId = complianceCase.id "
            + "and complianceCase.companyId = :companyId and complianceCase.siteId = :siteId")
    Optional<ClientObservation> findInScope(@Param("id") Long id,
                                             @Param("companyId") Long companyId,
                                             @Param("siteId") Long siteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClientObservation> findFirstBySeriesIdOrderByRevisionAsc(UUID seriesId);

    Optional<ClientObservation> findFirstBySeriesIdOrderByRevisionDesc(UUID seriesId);

    List<ClientObservation> findAllByComplianceCaseIdOrderByRecordedAtAscIdAsc(Long complianceCaseId);
}
