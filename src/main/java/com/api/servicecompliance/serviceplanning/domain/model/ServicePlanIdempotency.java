package com.api.servicecompliance.serviceplanning.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "service_plan_idempotency", uniqueConstraints = @UniqueConstraint(
        name = "uq_service_plan_idempotency_scope",
        columnNames = {"company_id", "actor_id", "operation", "idempotency_key"}))
public class ServicePlanIdempotency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 80)
    private String operation;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private Long servicePlanId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected ServicePlanIdempotency() {
    }

    public ServicePlanIdempotency(Long companyId, Long actorId, String operation,
                                  String idempotencyKey, String requestHash, Long servicePlanId,
                                  Instant createdAt, Instant expiresAt) {
        this.companyId = companyId;
        this.actorId = actorId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.servicePlanId = servicePlanId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getRequestHash() { return requestHash; }
    public Long getServicePlanId() { return servicePlanId; }
    public Instant getExpiresAt() { return expiresAt; }
}
