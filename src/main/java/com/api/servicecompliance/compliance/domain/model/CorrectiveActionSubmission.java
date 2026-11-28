package com.api.servicecompliance.compliance.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "corrective_action_submissions")
public class CorrectiveActionSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long correctiveActionId;
    @Column(nullable = false)
    private int submissionNumber;
    @Column(nullable = false)
    private long actionVersion;
    @Column(nullable = false, length = 2000)
    private String details;
    @Column(nullable = false)
    private Long submittedBy;
    @Column(nullable = false)
    private Instant submittedAt;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "submission_id", nullable = false)
    @OrderBy("evidenceOrder ASC")
    private List<CorrectiveActionEvidence> evidence = new ArrayList<>();

    protected CorrectiveActionSubmission() {}

    public CorrectiveActionSubmission(Long correctiveActionId, int submissionNumber, long actionVersion,
                                      String details, Long submittedBy, Instant submittedAt,
                                      List<CorrectiveActionEvidence> evidence) {
        this.correctiveActionId = correctiveActionId;
        this.submissionNumber = submissionNumber;
        this.actionVersion = actionVersion;
        this.details = details;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.evidence.addAll(evidence);
    }

    public Long getId() { return id; }
    public Long getCorrectiveActionId() { return correctiveActionId; }
    public int getSubmissionNumber() { return submissionNumber; }
    public long getActionVersion() { return actionVersion; }
    public String getDetails() { return details; }
    public Long getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public List<CorrectiveActionEvidence> getEvidence() { return List.copyOf(evidence); }
}
