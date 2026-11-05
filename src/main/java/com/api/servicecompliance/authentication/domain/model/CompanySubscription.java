package com.api.servicecompliance.authentication.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "company_subscriptions")
public class CompanySubscription {
    @Id
    private Long companyId;

    @Column(unique = true)
    private String stripeCustomerId;

    @Column(unique = true)
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionPlan plan;

    @Column(nullable = false)
    private int seatLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CompanySubscription() {
    }

    public CompanySubscription(Long companyId, SubscriptionPlan plan, int seatLimit, SubscriptionStatus status) {
        if (plan == SubscriptionPlan.PLUS && seatLimit != 3) {
            throw new IllegalArgumentException("PLUS subscriptions must have exactly 3 seats");
        }
        if (seatLimit < 1) {
            throw new IllegalArgumentException("Seat limit must be positive");
        }
        this.companyId = companyId;
        this.plan = plan;
        this.seatLimit = seatLimit;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public int getSeatLimit() {
        return seatLimit;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }
}
