package com.api.servicecompliance.authentication.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sites")
public class SiteScope {
    @Id
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    protected SiteScope() {
    }

    public SiteScope(Long id, Long companyId, String name) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }
}
