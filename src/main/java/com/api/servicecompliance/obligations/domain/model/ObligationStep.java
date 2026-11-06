package com.api.servicecompliance.obligations.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "obligation_steps")
public class ObligationStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(length = 2000)
    private String instructions;
    @Column(name = "step_order", nullable = false)
    private int position;

    protected ObligationStep() {}

    public ObligationStep(String title, String instructions, int position) {
        this.title = title;
        this.instructions = instructions;
        this.position = position;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getInstructions() { return instructions; }
    public int getPosition() { return position; }
}
