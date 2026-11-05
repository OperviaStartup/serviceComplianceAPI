package com.api.servicecompliance.serviceplanning.domain.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "obligation_definitions")
public class ObligationDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long servicePlanId;
    @Column(nullable = false) private Long siteId;
    @Column(nullable = false) private String activity;
    @Column(nullable = false, length = 2000) private String instructions;
    @Column(nullable = false, length = 2000) private String acceptanceCriteria;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RecurrenceType recurrenceType;
    private LocalDate occurrenceDate;
    @Column(nullable = false) private int recurrenceInterval;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "obligation_definition_weekdays", joinColumns = @JoinColumn(name = "definition_id"))
    @Column(name = "day_of_week", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> daysOfWeek = new LinkedHashSet<>();
    @Column(nullable = false) private LocalTime localStartTime;
    @Column(nullable = false) private int windowDurationMinutes;
    @Column(nullable = false, length = 64) private String timezone;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "definition_id", nullable = false)
    private List<EvidenceRequirement> evidenceRequirements = new ArrayList<>();
    @Column(nullable = false) private Long createdBy;
    @Column(nullable = false) private Long updatedBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected ObligationDefinition() {}

    public ObligationDefinition(Long planId, Long siteId, String activity, String instructions,
                                String acceptanceCriteria, RecurrenceType recurrenceType,
                                LocalDate occurrenceDate, int recurrenceInterval, Set<DayOfWeek> daysOfWeek,
                                LocalTime localStartTime, int windowDurationMinutes,
                                List<EvidenceRequirement> requirements, Long actorId, Instant now) {
        this.servicePlanId = planId;
        this.siteId = siteId;
        this.createdBy = actorId;
        this.createdAt = now;
        update(activity, instructions, acceptanceCriteria, recurrenceType, occurrenceDate,
                recurrenceInterval, daysOfWeek, localStartTime, windowDurationMinutes,
                requirements, actorId, now);
    }

    public void update(String activity, String instructions, String acceptanceCriteria,
                       RecurrenceType recurrenceType, LocalDate occurrenceDate, int recurrenceInterval,
                       Set<DayOfWeek> daysOfWeek, LocalTime localStartTime, int windowDurationMinutes,
                       List<EvidenceRequirement> requirements, Long actorId, Instant now) {
        this.activity = activity;
        this.instructions = instructions;
        this.acceptanceCriteria = acceptanceCriteria;
        this.recurrenceType = recurrenceType;
        this.occurrenceDate = occurrenceDate;
        this.recurrenceInterval = recurrenceInterval;
        this.daysOfWeek.clear();
        this.daysOfWeek.addAll(daysOfWeek);
        this.localStartTime = localStartTime;
        this.windowDurationMinutes = windowDurationMinutes;
        this.timezone = ServicePlan.TIMEZONE;
        this.evidenceRequirements.clear();
        this.evidenceRequirements.addAll(requirements);
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getServicePlanId() { return servicePlanId; }
    public Long getSiteId() { return siteId; }
    public String getActivity() { return activity; }
    public String getInstructions() { return instructions; }
    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public RecurrenceType getRecurrenceType() { return recurrenceType; }
    public LocalDate getOccurrenceDate() { return occurrenceDate; }
    public int getRecurrenceInterval() { return recurrenceInterval; }
    public Set<DayOfWeek> getDaysOfWeek() { return Set.copyOf(daysOfWeek); }
    public LocalTime getLocalStartTime() { return localStartTime; }
    public int getWindowDurationMinutes() { return windowDurationMinutes; }
    public String getTimezone() { return timezone; }
    public List<EvidenceRequirement> getEvidenceRequirements() { return List.copyOf(evidenceRequirements); }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
