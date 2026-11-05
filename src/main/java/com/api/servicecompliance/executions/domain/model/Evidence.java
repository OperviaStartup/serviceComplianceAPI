package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity @Table(name="evidences")
public class Evidence {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long executionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private EvidenceType type;
    @Column(length=1000) private String url;
    @Column(length=1000) private String description;
    @Column(nullable=false) private Instant createdAt;
    private Long photoRequirementId;
    @Column(length=500) private String storageKey;
    @Column(length=30) private String contentType;
    private Long fileSize;
    @Column(length=64) private String checksum;
    private Instant capturedAt;
    protected Evidence(){}
    public Evidence(Long executionId,EvidenceType type,String url,String description){this.executionId=executionId;this.type=type;this.url=url;this.description=description;this.createdAt=Instant.now();}
    public static Evidence photo(Long executionId, Long requirementId, String storageKey, String contentType,
                                 long size, String checksum, Instant capturedAt, Instant now) {
        Evidence evidence = new Evidence();
        evidence.executionId=executionId; evidence.type=EvidenceType.PHOTO;
        evidence.photoRequirementId=requirementId; evidence.storageKey=storageKey;
        evidence.contentType=contentType; evidence.fileSize=size; evidence.checksum=checksum;
        evidence.capturedAt=capturedAt; evidence.createdAt=now;
        return evidence;
    }
    public Long getPhotoRequirementId() { return photoRequirementId; }
    @JsonIgnore public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public Long getFileSize() { return fileSize; }
    public String getChecksum() { return checksum; }
    public Instant getCapturedAt() { return capturedAt; }
    public Long getId(){return id;} public Long getExecutionId(){return executionId;} public EvidenceType getType(){return type;} public String getUrl(){return url;} public String getDescription(){return description;} public Instant getCreatedAt(){return createdAt;}
}
