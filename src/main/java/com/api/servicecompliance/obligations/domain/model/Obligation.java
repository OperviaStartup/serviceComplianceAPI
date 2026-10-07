package com.api.servicecompliance.obligations.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="obligations")
public class Obligation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String title;
    @Column(nullable=false,length=1000) private String description;
    @Column(nullable=false) private String siteName;
    @Column(nullable=false) private Long assignedOperatorId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ObligationStatus status;
    @Column(nullable=false) private Instant dueAt;
    protected Obligation(){}
    public Obligation(String title,String description,String siteName,Long assignedOperatorId,Instant dueAt){this.title=title;this.description=description;this.siteName=siteName;this.assignedOperatorId=assignedOperatorId;this.dueAt=dueAt;this.status=ObligationStatus.ASSIGNED;}
    public Long getId(){return id;} public String getTitle(){return title;} public String getDescription(){return description;} public String getSiteName(){return siteName;} public Long getAssignedOperatorId(){return assignedOperatorId;} public ObligationStatus getStatus(){return status;} public Instant getDueAt(){return dueAt;}
}
