package com.api.servicecompliance.compliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "compliance_cases")
public class ComplianceCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long obligationId;
    @Column(nullable = false)
    private Long companyId;
    @Column(nullable = false)
    private Long siteId;
    @Column(nullable = false)
    private long decisionVersion;
    @Column(nullable = false)
    private Long createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected ComplianceCase() {}

    public ComplianceCase(Long obligationId, Long companyId, Long siteId, Long createdBy, Instant now) {
        this.obligationId = obligationId;
        this.companyId = companyId;
        this.siteId = siteId;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public long appendDecision(Instant now) {
        decisionVersion++;
        updatedAt = now;
        return decisionVersion;
    }

    public Long getId() { return id; }
    public Long getObligationId() { return obligationId; }
    public Long getCompanyId() { return companyId; }
    public Long getSiteId() { return siteId; }
    public long getDecisionVersion() { return decisionVersion; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
