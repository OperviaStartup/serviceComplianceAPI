package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offline_sync_operations", uniqueConstraints = @UniqueConstraint(
        name = "uq_offline_sync_operation_scope",
        columnNames = {"company_id", "actor_id", "client_operation_id"}))
public class OfflineSyncOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long companyId;
    @Column(nullable=false) private Long actorId;
    @Column(nullable=false) private UUID clientOperationId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private SyncOperationType operationType;
    @Column(nullable=false,length=64) private String requestHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private SyncOutcome outcome;
    @Column(nullable=false,length=80) private String resultCode;
    @Column(length=30) private String resourceType;
    private Long resourceId;
    private Long currentVersion;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant expiresAt;

    protected OfflineSyncOperation() {}

    public OfflineSyncOperation(Long companyId, Long actorId, UUID clientOperationId,
                                SyncOperationType operationType, String requestHash,
                                SyncOutcome outcome, String resultCode, String resourceType,
                                Long resourceId, Long currentVersion, Instant createdAt,
                                Instant expiresAt) {
        this.companyId=companyId; this.actorId=actorId; this.clientOperationId=clientOperationId;
        this.operationType=operationType; this.requestHash=requestHash; this.outcome=outcome;
        this.resultCode=resultCode; this.resourceType=resourceType; this.resourceId=resourceId;
        this.currentVersion=currentVersion; this.createdAt=createdAt; this.expiresAt=expiresAt;
    }

    public UUID getClientOperationId() { return clientOperationId; }
    public SyncOperationType getOperationType() { return operationType; }
    public String getRequestHash() { return requestHash; }
    public SyncOutcome getOutcome() { return outcome; }
    public String getResultCode() { return resultCode; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public Long getCurrentVersion() { return currentVersion; }
}
