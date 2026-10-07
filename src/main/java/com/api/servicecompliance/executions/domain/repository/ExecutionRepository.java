package com.api.servicecompliance.executions.domain.repository;
import com.api.servicecompliance.executions.domain.model.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExecutionRepository extends JpaRepository<Execution,Long>{}
