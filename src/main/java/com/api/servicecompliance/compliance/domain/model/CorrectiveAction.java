package com.api.servicecompliance.compliance.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "corrective_actions")
public class CorrectiveAction {
    public static final String TIMEZONE = "America/Lima";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long complianceCaseId;
    @Column(nullable = false)
    private Long sourceComplianceResultId;
    @Column(nullable = false)
    private Long companyId;
    @Column(nullable = false)
    private Long siteId;
    @Column(nullable = false)
    private Long assigneeId;
    @Column(nullable = false, length = 4000)
    private String description;
    @Column(nullable = false)
    private LocalDate targetDate;
    @Column(nullable = false, length = 64)
    private String timezone = TIMEZONE;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CorrectiveActionState state;
    @Column(nullable = false)
    private Long createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Long updatedBy;
    @Column(nullable = false)
    private Instant updatedAt;
    private Long closedBy;
    private Instant closedAt;
    @Version
    @Column(nullable = false)
    private long version;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "corrective_action_id", nullable = false)
    @OrderBy("actionVersion ASC")
    private List<CorrectiveActionEvent> events = new ArrayList<>();

    protected CorrectiveAction() {}

    public CorrectiveAction(Long complianceCaseId, Long sourceComplianceResultId,
                            Long companyId, Long siteId, Long assigneeId,
                            String description, LocalDate targetDate,
                            Long createdBy, Instant now) {
        this.complianceCaseId = complianceCaseId;
        this.sourceComplianceResultId = sourceComplianceResultId;
        this.companyId = companyId;
        this.siteId = siteId;
        this.assigneeId = assigneeId;
        this.description = description;
        this.targetDate = targetDate;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedBy = createdBy;
        this.updatedAt = now;
        this.state = CorrectiveActionState.ASSIGNED;
        events.add(new CorrectiveActionEvent(0, null, state, createdBy,
                "Corrective action assigned", now));
    }

    public void submitAttention(Long actorId, String reason, Instant now) {
        requireState(CorrectiveActionState.ASSIGNED, CorrectiveActionState.RETURNED);
        transition(CorrectiveActionState.ATTENTION_SUBMITTED, actorId, reason, now);
    }

    public void returnForCorrection(Long actorId, String reason, Instant now) {
        requireState(CorrectiveActionState.ATTENTION_SUBMITTED);
        transition(CorrectiveActionState.RETURNED, actorId, reason, now);
    }

    public void verifyAndClose(Long actorId, String reason, Instant now) {
        requireState(CorrectiveActionState.ATTENTION_SUBMITTED);
        transition(CorrectiveActionState.CLOSED, actorId, reason, now);
        closedBy = actorId;
        closedAt = now;
    }

    public CorrectiveActionStatus statusAt(Instant now) {
        if (isOverdue(now)) return CorrectiveActionStatus.OVERDUE;
        return CorrectiveActionStatus.valueOf(state.name());
    }

    public boolean isOverdue(Instant now) {
        if (state != CorrectiveActionState.ASSIGNED && state != CorrectiveActionState.RETURNED) {
            return false;
        }
        Instant overdueAt = targetDate.plusDays(1).atStartOfDay(ZoneId.of(timezone)).toInstant();
        return !now.isBefore(overdueAt);
    }

    private void transition(CorrectiveActionState nextState, Long actorId, String reason, Instant now) {
        CorrectiveActionState previous = state;
        state = nextState;
        updatedBy = actorId;
        updatedAt = now;
        events.add(new CorrectiveActionEvent(version + 1, previous, nextState, actorId, reason, now));
    }

    private void requireState(CorrectiveActionState... allowed) {
        for (CorrectiveActionState candidate : allowed) {
            if (state == candidate) return;
        }
        throw new IllegalStateException("Corrective action cannot transition from " + state);
    }

    public Long getId() { return id; }
    public Long getComplianceCaseId() { return complianceCaseId; }
    public Long getSourceComplianceResultId() { return sourceComplianceResultId; }
    public Long getCompanyId() { return companyId; }
    public Long getSiteId() { return siteId; }
    public Long getAssigneeId() { return assigneeId; }
    public String getDescription() { return description; }
    public LocalDate getTargetDate() { return targetDate; }
    public String getTimezone() { return timezone; }
    public CorrectiveActionState getState() { return state; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getClosedBy() { return closedBy; }
    public Instant getClosedAt() { return closedAt; }
    public long getVersion() { return version; }
    public List<CorrectiveActionEvent> getEvents() { return List.copyOf(events); }
}
