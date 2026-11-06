package com.api.servicecompliance.obligations.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="obligations")
public class Obligation {
    public static final String TIMEZONE = "America/Lima";
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long companyId;
    @Column(nullable=false) private Long siteId;
    @Column(nullable=false) private String title;
    @Column(nullable=false,length=1000) private String description;
    @Column(nullable=false) private String siteName;
    private Long assignedOperatorId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ObligationStatus status;
    @Column(nullable=false) private Instant dueAt;
    private Instant startsAt;
    private Long sourcePlanId;
    private Integer sourcePlanVersion;
    private Long sourceDefinitionId;
    private Long sourceDefinitionVersion;
    private java.time.LocalDate occurrenceDate;
    @Column(length = 64) private String timezone;
    @Column(nullable = false) private long assignmentVersion;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "obligation_id", nullable = false)
    @OrderBy("position ASC")
    private List<ObligationStep> steps = new ArrayList<>();
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "obligation_id", nullable = false)
    @OrderBy("position ASC")
    private List<ObligationPhotoRequirement> photoRequirements = new ArrayList<>();
    protected Obligation(){}
    public Obligation(String title,String description,String siteName,Long assignedOperatorId,Instant dueAt,Long companyId,Long siteId){this.companyId=java.util.Objects.requireNonNull(companyId);this.siteId=java.util.Objects.requireNonNull(siteId);this.title=title;this.description=description;this.siteName=siteName;this.assignedOperatorId=assignedOperatorId;this.dueAt=dueAt;this.status=ObligationStatus.ASSIGNED;}
    public Obligation(String title, String description, String siteName, Long assignedOperatorId,
                      Instant startsAt, Instant dueAt, Long companyId, Long siteId,
                      List<ObligationStep> steps, List<ObligationPhotoRequirement> photoRequirements) {
        this(title, description, siteName, assignedOperatorId, dueAt, companyId, siteId);
        this.startsAt = startsAt;
        this.steps.addAll(steps);
        this.photoRequirements.addAll(photoRequirements);
    }
    public Obligation(String title, String description, String siteName, Instant startsAt, Instant dueAt,
                      Long companyId, Long siteId, Long sourcePlanId, Integer sourcePlanVersion,
                      Long sourceDefinitionId, Long sourceDefinitionVersion,
                      java.time.LocalDate occurrenceDate, List<ObligationStep> steps,
                      List<ObligationPhotoRequirement> photoRequirements) {
        this(title, description, siteName, null, startsAt, dueAt, companyId, siteId, steps, photoRequirements);
        this.sourcePlanId = sourcePlanId;
        this.sourcePlanVersion = sourcePlanVersion;
        this.sourceDefinitionId = sourceDefinitionId;
        this.sourceDefinitionVersion = sourceDefinitionVersion;
        this.occurrenceDate = occurrenceDate;
        this.timezone = TIMEZONE;
    }
    public Instant getStartsAt() { return startsAt; }
    public void markInProgress() { status = ObligationStatus.IN_PROGRESS; }
    public void markSubmitted() { status = ObligationStatus.SUBMITTED; }
    public boolean isExpired(Instant now) {
        return status != ObligationStatus.SUBMITTED && !now.isBefore(dueAt);
    }
    public ObligationStatus statusAt(Instant now) {
        return startsAt!=null && isExpired(now) ? ObligationStatus.OVERDUE : status;
    }
    public List<ObligationStep> getSteps() { return List.copyOf(steps); }
    public List<ObligationPhotoRequirement> getPhotoRequirements() { return List.copyOf(photoRequirements); }
    public void assignTo(Long operatorId) { assignedOperatorId = java.util.Objects.requireNonNull(operatorId); assignmentVersion++; }
    public Long getCompanyId(){return companyId;} public Long getSiteId(){return siteId;}
    public Long getId(){return id;} public String getTitle(){return title;} public String getDescription(){return description;} public String getSiteName(){return siteName;} public Long getAssignedOperatorId(){return assignedOperatorId;} public ObligationStatus getStatus(){return status;} public Instant getDueAt(){return dueAt;}
    public Long getSourcePlanId() { return sourcePlanId; }
    public Integer getSourcePlanVersion() { return sourcePlanVersion; }
    public Long getSourceDefinitionId() { return sourceDefinitionId; }
    public Long getSourceDefinitionVersion() { return sourceDefinitionVersion; }
    public java.time.LocalDate getOccurrenceDate() { return occurrenceDate; }
    public String getTimezone() { return timezone == null ? TIMEZONE : timezone; }
    public long getAssignmentVersion() { return assignmentVersion; }
}
