package com.api.servicecompliance.compliance.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "corrective_action_evidence")
public class CorrectiveActionEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "submission_id", nullable = false, insertable = false, updatable = false)
    private Long submissionId;
    @Column(nullable = false)
    private Long correctiveActionId;
    @Column(nullable = false)
    private int evidenceOrder;
    @JsonIgnore
    @Column(nullable = false, length = 500)
    private String storageKey;
    @Column(nullable = false, length = 30)
    private String contentType;
    @Column(nullable = false)
    private long fileSize;
    @Column(nullable = false, length = 64)
    private String checksum;
    @Column(nullable = false)
    private Instant capturedAt;
    @Column(nullable = false)
    private Long createdBy;
    @Column(nullable = false)
    private Instant createdAt;

    protected CorrectiveActionEvidence() {}

    public CorrectiveActionEvidence(Long correctiveActionId, int evidenceOrder, String storageKey,
                                    String contentType, long fileSize, String checksum,
                                    Instant capturedAt, Long createdBy, Instant createdAt) {
        this.correctiveActionId = correctiveActionId;
        this.evidenceOrder = evidenceOrder;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.capturedAt = capturedAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getSubmissionId() { return submissionId; }
    public Long getCorrectiveActionId() { return correctiveActionId; }
    public int getEvidenceOrder() { return evidenceOrder; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getChecksum() { return checksum; }
    public Instant getCapturedAt() { return capturedAt; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
