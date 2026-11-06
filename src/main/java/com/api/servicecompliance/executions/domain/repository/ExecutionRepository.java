package com.api.servicecompliance.executions.domain.repository;
import com.api.servicecompliance.executions.domain.model.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ExecutionRepository extends JpaRepository<Execution,Long> {
    List<Execution> findByObligationId(Long obligationId);
    @Query("select e.obligationId from Execution e where e.id = :id")
    Optional<Long> findObligationId(@Param("id") Long id);
}
