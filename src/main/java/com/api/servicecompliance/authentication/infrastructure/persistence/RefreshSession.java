package com.api.servicecompliance.authentication.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private String familyId;
    @Column(nullable = false, unique = true) private String tokenHash;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private boolean used;
    @Column(nullable = false) private boolean revoked;
    protected RefreshSession() {}
    public RefreshSession(Long userId, String familyId, String tokenHash, Instant expiresAt) {
        this.userId = userId; this.familyId = familyId; this.tokenHash = tokenHash; this.expiresAt = expiresAt;
    }
    public Long getUserId() { return userId; }
    public String getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public boolean isRevoked() { return revoked; }
    public void markUsed() { used = true; }
}
