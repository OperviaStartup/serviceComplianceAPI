package com.api.servicecompliance.obligations.domain.repository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ObligationRepository extends JpaRepository<Obligation,Long>{List<Obligation> findByAssignedOperatorId(Long operatorId);}
