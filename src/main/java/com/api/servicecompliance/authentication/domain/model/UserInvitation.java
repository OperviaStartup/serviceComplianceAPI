package com.api.servicecompliance.authentication.domain.model;

import com.api.servicecompliance.shared.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_invitations")
public class UserInvitation {
    @Id
    private String id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long siteId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 8)
    private String documentNumber;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationDeliveryStatus deliveryStatus;

    @Column(nullable = false)
    private int deliveryAttempt;

    private String resendEmailId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Long createdByUserId;

    private Long acceptedUserId;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant acceptedAt;

    protected UserInvitation() {
    }

    public UserInvitation(Long companyId, Long siteId, String email, String documentNumber,
                          String fullName, Role role, String tokenHash, Instant expiresAt,
                          Long createdByUserId) {
        this.id = UUID.randomUUID().toString();
        this.companyId = companyId;
        this.siteId = siteId;
        this.email = email;
        this.documentNumber = documentNumber;
        this.fullName = fullName;
        this.role = role;
        this.tokenHash = tokenHash;
        this.status = InvitationStatus.PENDING;
        this.deliveryStatus = InvitationDeliveryStatus.PENDING;
        this.deliveryAttempt = 1;
        this.expiresAt = expiresAt;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
    }

    public boolean isLiveAt(Instant now) {
        return status == InvitationStatus.PENDING && expiresAt.isAfter(now);
    }

    public void accept(Long userId, Instant now) {
        status = InvitationStatus.ACCEPTED;
        acceptedUserId = userId;
        acceptedAt = now;
    }

    public void expire() {
        status = InvitationStatus.EXPIRED;
    }

    public void cancel() {
        status = InvitationStatus.CANCELLED;
    }

    public void prepareRedelivery(String newTokenHash, Instant newExpiresAt) {
        tokenHash = newTokenHash;
        expiresAt = newExpiresAt;
        deliveryStatus = InvitationDeliveryStatus.PENDING;
        resendEmailId = null;
        deliveryAttempt++;
    }

    public void markSent(String emailId) {
        deliveryStatus = InvitationDeliveryStatus.SENT;
        resendEmailId = emailId;
    }

    public void markDeliveryFailed() {
        deliveryStatus = InvitationDeliveryStatus.FAILED;
    }

    public String getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getSiteId() { return siteId; }
    public String getEmail() { return email; }
    public String getDocumentNumber() { return documentNumber; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public InvitationStatus getStatus() { return status; }
    public InvitationDeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public int getDeliveryAttempt() { return deliveryAttempt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Long getAcceptedUserId() { return acceptedUserId; }
}
