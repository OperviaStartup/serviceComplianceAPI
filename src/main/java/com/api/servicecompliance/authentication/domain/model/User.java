package com.api.servicecompliance.authentication.domain.model;

import com.api.servicecompliance.shared.domain.Role;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String fullName;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private long sessionVersion;
    @Column(nullable = false) private Long companyId;
    @Column(nullable = false) private Long siteId;
    @Column(nullable = false) private Instant createdAt;
    protected User() {}

    public User(String fullName, String email, String passwordHash, Role role, Long companyId, Long siteId) {
        this.companyId = java.util.Objects.requireNonNull(companyId);
        this.siteId = java.util.Objects.requireNonNull(siteId);
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public void suspend() {
        active = false;
        sessionVersion++;
    }

    public Long getId(){return id;}
    public String getFullName(){return fullName;}
    public String getEmail(){return email;}
    public String getPasswordHash(){return passwordHash;}
    public Role getRole(){return role;}
    public boolean isActive(){return active;}
    public long getSessionVersion(){return sessionVersion;}
    public Long getCompanyId(){return companyId;}
    public Long getSiteId(){return siteId;}
}
