package com.api.servicecompliance.obligations.domain.model;

import com.api.servicecompliance.executions.domain.model.EvidenceType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "obligation_evidence_requirements")
public class ObligationEvidenceRequirement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long sourceRequirementId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EvidenceType evidenceType;
    @Column(name = "is_required", nullable = false) private boolean required;
    @Column(nullable = false) private int minimumCount;
    @Column(nullable = false) private int maximumCount;
    @Column(name = "requirement_order", nullable = false) private int position;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "obligation_evidence_requirement_constraints",
            joinColumns = @JoinColumn(name = "obligation_evidence_requirement_id"))
    @MapKeyColumn(name = "constraint_key", length = 50)
    @Column(name = "constraint_value", nullable = false, length = 255)
    private Map<String, String> constraints = new LinkedHashMap<>();

    protected ObligationEvidenceRequirement() {}

    public ObligationEvidenceRequirement(Long sourceRequirementId, EvidenceType evidenceType, boolean required,
                                         int minimumCount, int maximumCount, int position,
                                         Map<String, String> constraints) {
        this.sourceRequirementId = sourceRequirementId;
        this.evidenceType = evidenceType;
        this.required = required;
        this.minimumCount = minimumCount;
        this.maximumCount = maximumCount;
        this.position = position;
        this.constraints.putAll(constraints);
    }

    public Long getId() { return id; }
    public Long getSourceRequirementId() { return sourceRequirementId; }
    public EvidenceType getEvidenceType() { return evidenceType; }
    public boolean isRequired() { return required; }
    public int getMinimumCount() { return minimumCount; }
    public int getMaximumCount() { return maximumCount; }
    public int getPosition() { return position; }
    public Map<String, String> getConstraints() { return Map.copyOf(constraints); }
}
