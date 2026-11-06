package com.api.servicecompliance.executions.domain.repository;
import com.api.servicecompliance.executions.domain.model.ExecutionStepProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ExecutionStepProgressRepository extends JpaRepository<ExecutionStepProgress,Long> {
    List<ExecutionStepProgress> findByExecutionId(Long executionId);
}
