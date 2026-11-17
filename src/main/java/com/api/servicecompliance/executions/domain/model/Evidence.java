package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;
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
    private Long evidenceRequirementId;
    private Long createdBy;
    @Column(length=512) private String textValue;
    @Column(precision=9, scale=6) private BigDecimal latitude;
    @Column(precision=9, scale=6) private BigDecimal longitude;
    @Column(precision=10, scale=2) private BigDecimal accuracyMeters;
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
    public static Evidence binary(Long executionId, Long requirementId, EvidenceType type, Long actorId,
                                  String storageKey, String contentType, long size, String checksum,
                                  BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                                  Instant capturedAt, Instant now) {
        Evidence evidence = new Evidence();
        evidence.executionId=executionId; evidence.evidenceRequirementId=requirementId;
        evidence.type=type; evidence.createdBy=actorId; evidence.storageKey=storageKey;
        evidence.contentType=contentType; evidence.fileSize=size; evidence.checksum=checksum;
        evidence.latitude=latitude; evidence.longitude=longitude; evidence.accuracyMeters=accuracyMeters;
        evidence.capturedAt=capturedAt; evidence.createdAt=now;
        return evidence;
    }
    public static Evidence qrCode(Long executionId, Long requirementId, Long actorId, String value,
                                  long size, String checksum, Instant capturedAt, Instant now) {
        Evidence evidence = new Evidence();
        evidence.executionId=executionId; evidence.evidenceRequirementId=requirementId;
        evidence.type=EvidenceType.QR_CODE; evidence.createdBy=actorId; evidence.textValue=value;
        evidence.fileSize=size; evidence.checksum=checksum;
        evidence.capturedAt=capturedAt; evidence.createdAt=now;
        return evidence;
    }
    public static Evidence location(Long executionId, Long requirementId, Long actorId,
                                    BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                                    long size, String checksum, Instant capturedAt, Instant now) {
        Evidence evidence = new Evidence();
        evidence.executionId=executionId; evidence.evidenceRequirementId=requirementId;
        evidence.type=EvidenceType.LOCATION; evidence.createdBy=actorId;
        evidence.latitude=latitude; evidence.longitude=longitude; evidence.accuracyMeters=accuracyMeters;
        evidence.fileSize=size; evidence.checksum=checksum;
        evidence.capturedAt=capturedAt; evidence.createdAt=now;
        return evidence;
    }
    public Long getPhotoRequirementId() { return photoRequirementId; }
    @JsonIgnore public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public Long getFileSize() { return fileSize; }
    public String getChecksum() { return checksum; }
    public Instant getCapturedAt() { return capturedAt; }
    public Long getEvidenceRequirementId() { return evidenceRequirementId; }
    public Long getCreatedBy() { return createdBy; }
    public String getTextValue() { return textValue; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getAccuracyMeters() { return accuracyMeters; }
    public Long getId(){return id;} public Long getExecutionId(){return executionId;} public EvidenceType getType(){return type;} public String getUrl(){return url;} public String getDescription(){return description;} public Instant getCreatedAt(){return createdAt;}
}
