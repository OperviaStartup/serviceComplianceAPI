package com.api.servicecompliance.compliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "compliance_result_criteria")
public class ComplianceCriterion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false, length = 100)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CriterionStatus status;
    @Column(length = 2000)
    private String notes;

    protected ComplianceCriterion() {}

    public ComplianceCriterion(int position, String code, CriterionStatus status, String notes) {
        this.position = position;
        this.code = code;
        this.status = status;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public int getPosition() { return position; }
    public String getCode() { return code; }
    public CriterionStatus getStatus() { return status; }
    public String getNotes() { return notes; }
}
