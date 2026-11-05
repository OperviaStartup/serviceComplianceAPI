package com.api.servicecompliance.serviceplanning.domain.model;

import jakarta.persistence.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "evidence_requirements")
public class EvidenceRequirement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EvidenceRequirementType evidenceType;
    @Column(name = "is_required", nullable = false) private boolean required;
    @Column(nullable = false) private int minimumCount;
    @Column(nullable = false) private int maximumCount;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evidence_requirement_constraints",
            joinColumns = @JoinColumn(name = "evidence_requirement_id"))
    @MapKeyColumn(name = "constraint_key", length = 50)
    @Column(name = "constraint_value", nullable = false, length = 255)
    private Map<String, String> constraints = new LinkedHashMap<>();

    protected EvidenceRequirement() {}

    public EvidenceRequirement(EvidenceRequirementType evidenceType, boolean required, int minimumCount,
                               int maximumCount, Map<String, String> constraints) {
        this.evidenceType = evidenceType;
        this.required = required;
        this.minimumCount = minimumCount;
        this.maximumCount = maximumCount;
        this.constraints.putAll(constraints);
    }

    public Long getId() { return id; }
    public EvidenceRequirementType getEvidenceType() { return evidenceType; }
    public boolean isRequired() { return required; }
    public int getMinimumCount() { return minimumCount; }
    public int getMaximumCount() { return maximumCount; }
    public Map<String, String> getConstraints() { return Map.copyOf(constraints); }
}
