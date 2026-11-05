package com.api.servicecompliance.serviceplanning.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_services")
public class CompanyService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected CompanyService() {
    }

    public CompanyService(Long companyId, String code, String name) {
        this.companyId = java.util.Objects.requireNonNull(companyId);
        this.code = java.util.Objects.requireNonNull(code);
        this.name = java.util.Objects.requireNonNull(name);
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public boolean isActive() {
        return active;
    }
}
