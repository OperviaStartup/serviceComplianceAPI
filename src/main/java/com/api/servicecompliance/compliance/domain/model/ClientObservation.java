package com.api.servicecompliance.compliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "client_observations")
public class ClientObservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long complianceCaseId;
    @Column(nullable = false)
    private UUID seriesId;
    @Column(nullable = false)
    private int revision;
    private Long supersedesObservationId;
    @Column(nullable = false, length = 4000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClientObservationOrigin origin;
    @Column(nullable = false)
    private LocalDate observedDate;
    @Column(length = 1000)
    private String amendmentReason;
    @Column(nullable = false)
    private Long recordedBy;
    @Column(nullable = false)
    private Instant recordedAt;

    protected ClientObservation() {}

    public ClientObservation(Long complianceCaseId, UUID seriesId, int revision,
                             Long supersedesObservationId, String description,
                             ClientObservationOrigin origin, LocalDate observedDate,
                             String amendmentReason, Long recordedBy, Instant recordedAt) {
        this.complianceCaseId = complianceCaseId;
        this.seriesId = seriesId;
        this.revision = revision;
        this.supersedesObservationId = supersedesObservationId;
        this.description = description;
        this.origin = origin;
        this.observedDate = observedDate;
        this.amendmentReason = amendmentReason;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public Long getComplianceCaseId() { return complianceCaseId; }
    public UUID getSeriesId() { return seriesId; }
    public int getRevision() { return revision; }
    public Long getSupersedesObservationId() { return supersedesObservationId; }
    public String getDescription() { return description; }
    public ClientObservationOrigin getOrigin() { return origin; }
    public LocalDate getObservedDate() { return observedDate; }
    public String getAmendmentReason() { return amendmentReason; }
    public Long getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
}
