package com.api.servicecompliance.serviceplanning.domain.repository;

import com.api.servicecompliance.serviceplanning.domain.model.CompanyService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface CompanyServiceRepository extends JpaRepository<CompanyService, Long> {
    boolean existsByIdAndCompanyIdAndActiveTrue(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select service from CompanyService service where service.id = :id and service.companyId = :companyId and service.active = true")
    Optional<CompanyService> lockActiveByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);
}
