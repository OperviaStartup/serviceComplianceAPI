package com.api.servicecompliance.executions.domain.repository;
import com.api.servicecompliance.executions.domain.model.OperationalException;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface OperationalExceptionRepository extends JpaRepository<OperationalException,Long> {
    List<OperationalException> findByExecutionIdOrderByReportedAtAscIdAsc(Long executionId);
    Optional<OperationalException> findByExecutionIdAndOperationId(Long executionId, UUID operationId);
}
