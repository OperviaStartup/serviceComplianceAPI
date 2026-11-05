package com.api.servicecompliance.serviceplanning.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "service_plans")
public class ServicePlan {
    public static final String TIMEZONE = "America/Lima";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long serviceId;

    @Column(nullable = false)
    private Long siteId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServicePlanState state;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ServicePlan() {
    }

    public ServicePlan(Long companyId, Long serviceId, Long siteId, LocalDate startDate,
                       LocalDate endDate, Long createdBy, Instant now) {
        this.companyId = java.util.Objects.requireNonNull(companyId);
        this.serviceId = java.util.Objects.requireNonNull(serviceId);
        this.siteId = java.util.Objects.requireNonNull(siteId);
        this.startDate = java.util.Objects.requireNonNull(startDate);
        this.endDate = java.util.Objects.requireNonNull(endDate);
        this.createdBy = java.util.Objects.requireNonNull(createdBy);
        this.createdAt = java.util.Objects.requireNonNull(now);
        this.updatedAt = now;
        this.timezone = TIMEZONE;
        this.state = ServicePlanState.DRAFT;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getServiceId() { return serviceId; }
    public Long getSiteId() { return siteId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getTimezone() { return timezone; }
    public ServicePlanState getState() { return state; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public boolean isDraft() { return state == ServicePlanState.DRAFT; }
}
