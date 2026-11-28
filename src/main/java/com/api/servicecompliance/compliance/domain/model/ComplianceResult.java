package com.api.servicecompliance.compliance.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "compliance_results")
public class ComplianceResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long complianceCaseId;
    @Column(nullable = false)
    private Long executionId;
    @Column(nullable = false)
    private long decisionVersion;
    private Long predecessorResultId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplianceOutcome outcome;
    @Column(nullable = false, length = 100)
    private String sourceReviewVersion;
    @Column(nullable = false)
    private long sourceExecutionVersion;
    private Long sourcePlanId;
    private Integer sourcePlanVersion;
    private Long sourceDefinitionId;
    private Long sourceDefinitionVersion;
    @Column(nullable = false, length = 4000)
    private String explanation;
    @Column(length = 2000)
    private String reevaluationReason;
    private Long sourceObservationId;
    @Column(nullable = false)
    private Long decidedBy;
    @Column(nullable = false)
    private Instant decidedAt;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "compliance_result_id", nullable = false)
    @OrderBy("position ASC")
    private List<ComplianceCriterion> criteria = new ArrayList<>();
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "compliance_result_id", nullable = false)
    @OrderBy("position ASC")
    private List<ComplianceFinding> findings = new ArrayList<>();

    protected ComplianceResult() {}

    public ComplianceResult(Long complianceCaseId, Long executionId, long decisionVersion,
                            Long predecessorResultId, ComplianceOutcome outcome, String sourceReviewVersion,
                            long sourceExecutionVersion, Long sourcePlanId, Integer sourcePlanVersion,
                            Long sourceDefinitionId, Long sourceDefinitionVersion, String explanation,
                            String reevaluationReason, Long sourceObservationId, Long decidedBy, Instant decidedAt,
                            List<ComplianceCriterion> criteria, List<ComplianceFinding> findings) {
        this.complianceCaseId = complianceCaseId;
        this.executionId = executionId;
        this.decisionVersion = decisionVersion;
        this.predecessorResultId = predecessorResultId;
        this.outcome = outcome;
        this.sourceReviewVersion = sourceReviewVersion;
        this.sourceExecutionVersion = sourceExecutionVersion;
        this.sourcePlanId = sourcePlanId;
        this.sourcePlanVersion = sourcePlanVersion;
        this.sourceDefinitionId = sourceDefinitionId;
        this.sourceDefinitionVersion = sourceDefinitionVersion;
        this.explanation = explanation;
        this.reevaluationReason = reevaluationReason;
        this.sourceObservationId = sourceObservationId;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.criteria.addAll(criteria);
        this.findings.addAll(findings);
    }

    public Long getId() { return id; }
    public Long getComplianceCaseId() { return complianceCaseId; }
    public Long getExecutionId() { return executionId; }
    public long getDecisionVersion() { return decisionVersion; }
    public Long getPredecessorResultId() { return predecessorResultId; }
    public ComplianceOutcome getOutcome() { return outcome; }
    public String getSourceReviewVersion() { return sourceReviewVersion; }
    public long getSourceExecutionVersion() { return sourceExecutionVersion; }
    public Long getSourcePlanId() { return sourcePlanId; }
    public Integer getSourcePlanVersion() { return sourcePlanVersion; }
    public Long getSourceDefinitionId() { return sourceDefinitionId; }
    public Long getSourceDefinitionVersion() { return sourceDefinitionVersion; }
    public String getExplanation() { return explanation; }
    public String getReevaluationReason() { return reevaluationReason; }
    public Long getSourceObservationId() { return sourceObservationId; }
    public Long getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public List<ComplianceCriterion> getCriteria() { return List.copyOf(criteria); }
    public List<ComplianceFinding> getFindings() { return List.copyOf(findings); }
}
