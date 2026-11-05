package com.api.servicecompliance.obligations.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "obligation_photo_requirements")
public class ObligationPhotoRequirement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(length = 2000)
    private String description;
    @Column(name = "photo_order", nullable = false)
    private int position;

    protected ObligationPhotoRequirement() {}

    public ObligationPhotoRequirement(String title, String description, int position) {
        this.title = title;
        this.description = description;
        this.position = position;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getPosition() { return position; }
}
