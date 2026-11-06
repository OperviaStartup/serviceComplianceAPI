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
import java.util.UUID;

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

    @Column(nullable = false)
    private UUID seriesId;

    @Column(nullable = false)
    private int businessVersion;

    private Long supersedesPlanId;

    private Long activatedBy;

    private Instant activatedAt;

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
        this.seriesId = UUID.randomUUID();
        this.businessVersion = 1;
    }

    public ServicePlan(ServicePlan source, Long actorId, Instant now) {
        this.companyId = source.companyId;
        this.serviceId = source.serviceId;
        this.siteId = source.siteId;
        this.startDate = source.startDate;
        this.endDate = source.endDate;
        this.timezone = source.timezone;
        this.state = ServicePlanState.DRAFT;
        this.createdBy = actorId;
        this.createdAt = now;
        this.updatedAt = now;
        this.seriesId = source.seriesId;
        this.businessVersion = source.businessVersion + 1;
        this.supersedesPlanId = source.id;
    }

    public void activate(Long actorId, Instant now) {
        state = ServicePlanState.ACTIVE;
        activatedBy = actorId;
        activatedAt = now;
        updatedAt = now;
    }

    public void supersede(Instant now) {
        state = ServicePlanState.SUPERSEDED;
        updatedAt = now;
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
    public boolean isActive() { return state == ServicePlanState.ACTIVE; }
    public UUID getSeriesId() { return seriesId; }
    public int getBusinessVersion() { return businessVersion; }
    public Long getSupersedesPlanId() { return supersedesPlanId; }
    public Long getActivatedBy() { return activatedBy; }
    public Instant getActivatedAt() { return activatedAt; }
}
