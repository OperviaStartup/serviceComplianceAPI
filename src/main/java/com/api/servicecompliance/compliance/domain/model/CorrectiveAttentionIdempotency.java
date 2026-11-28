package com.api.servicecompliance.compliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "corrective_attention_idempotency", uniqueConstraints = @UniqueConstraint(
        name = "uq_corrective_attention_idempotency_scope",
        columnNames = {"company_id", "actor_id", "command", "idempotency_key"}))
public class CorrectiveAttentionIdempotency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long companyId;
    @Column(nullable = false)
    private Long actorId;
    @Column(nullable = false, length = 80)
    private String command;
    @Column(nullable = false, length = 100)
    private String idempotencyKey;
    @Column(nullable = false, length = 64)
    private String requestHash;
    @Column(nullable = false)
    private Long submissionId;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;

    protected CorrectiveAttentionIdempotency() {}

    public CorrectiveAttentionIdempotency(Long companyId, Long actorId, String command,
                                          String idempotencyKey, String requestHash, Long submissionId,
                                          Instant createdAt, Instant expiresAt) {
        this.companyId = companyId;
        this.actorId = actorId;
        this.command = command;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.submissionId = submissionId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getRequestHash() { return requestHash; }
    public Long getSubmissionId() { return submissionId; }
}
