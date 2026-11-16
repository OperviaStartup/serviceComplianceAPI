package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "evidence_idempotency", uniqueConstraints = @UniqueConstraint(
        name = "uq_evidence_idempotency_scope",
        columnNames = {"company_id", "actor_id", "command", "idempotency_key"}))
public class EvidenceIdempotency {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long companyId;
    @Column(nullable = false) private Long actorId;
    @Column(nullable = false, length = 80) private String command;
    @Column(nullable = false, length = 100) private String idempotencyKey;
    @Column(nullable = false, length = 64) private String requestHash;
    @Column(nullable = false) private Long evidenceId;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;

    protected EvidenceIdempotency() {}

    public EvidenceIdempotency(Long companyId, Long actorId, String command, String idempotencyKey,
                               String requestHash, Long evidenceId, Instant createdAt, Instant expiresAt) {
        this.companyId=companyId; this.actorId=actorId; this.command=command;
        this.idempotencyKey=idempotencyKey; this.requestHash=requestHash;
        this.evidenceId=evidenceId; this.createdAt=createdAt; this.expiresAt=expiresAt;
    }

    public String getRequestHash() { return requestHash; }
    public Long getEvidenceId() { return evidenceId; }
}
