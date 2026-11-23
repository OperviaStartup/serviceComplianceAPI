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
@Table(name = "compliance_result_findings")
public class ComplianceFinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false, length = 100)
    private String code;
    @Column(nullable = false, length = 2000)
    private String description;
    private Long operationalExceptionId;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ExceptionDisposition exceptionDisposition;

    protected ComplianceFinding() {}

    public ComplianceFinding(int position, String code, String description, Long operationalExceptionId,
                             ExceptionDisposition exceptionDisposition) {
        this.position = position;
        this.code = code;
        this.description = description;
        this.operationalExceptionId = operationalExceptionId;
        this.exceptionDisposition = exceptionDisposition;
    }

    public Long getId() { return id; }
    public int getPosition() { return position; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public Long getOperationalExceptionId() { return operationalExceptionId; }
    public ExceptionDisposition getExceptionDisposition() { return exceptionDisposition; }
}
