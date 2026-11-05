package com.api.servicecompliance.authentication.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_families")
public class RefreshFamily {
    @Id private String id;
    protected RefreshFamily() {}
    public RefreshFamily(String id) { this.id = id; }
}
