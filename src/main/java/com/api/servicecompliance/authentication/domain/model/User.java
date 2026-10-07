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
    @Column(nullable = false) private Instant createdAt;
    protected User() {}
    public User(String fullName, String email, String passwordHash, Role role) { this.fullName=fullName; this.email=email; this.passwordHash=passwordHash; this.role=role; this.createdAt=Instant.now(); }
    public Long getId(){return id;} public String getFullName(){return fullName;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;} public Role getRole(){return role;}
}
