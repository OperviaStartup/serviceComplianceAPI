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

@Entity
@Table(name = "corrective_action_events")
public class CorrectiveActionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private long actionVersion;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CorrectiveActionState fromState;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CorrectiveActionState toState;
    @Column(nullable = false)
    private Long actorId;
    @Column(nullable = false, length = 2000)
    private String reason;
    @Column(nullable = false)
    private Instant occurredAt;

    protected CorrectiveActionEvent() {}

    public CorrectiveActionEvent(long actionVersion, CorrectiveActionState fromState,
                                 CorrectiveActionState toState, Long actorId,
                                 String reason, Instant occurredAt) {
        this.actionVersion = actionVersion;
        this.fromState = fromState;
        this.toState = toState;
        this.actorId = actorId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public long getActionVersion() { return actionVersion; }
    public CorrectiveActionState getFromState() { return fromState; }
    public CorrectiveActionState getToState() { return toState; }
    public Long getActorId() { return actorId; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
