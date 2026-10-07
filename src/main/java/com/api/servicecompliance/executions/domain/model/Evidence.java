package com.api.servicecompliance.executions.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="evidences")
public class Evidence {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long executionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private EvidenceType type;
    @Column(nullable=false,length=1000) private String url;
    @Column(length=1000) private String description;
    @Column(nullable=false) private Instant createdAt;
    protected Evidence(){}
    public Evidence(Long executionId,EvidenceType type,String url,String description){this.executionId=executionId;this.type=type;this.url=url;this.description=description;this.createdAt=Instant.now();}
    public Long getId(){return id;} public Long getExecutionId(){return executionId;} public EvidenceType getType(){return type;} public String getUrl(){return url;} public String getDescription(){return description;} public Instant getCreatedAt(){return createdAt;}
}
