package com.api.servicecompliance.authentication.application;

import java.time.Instant;

public final class InvitationDeliveryRequested {
    private final String invitationId;
    private final String email;
    private final String fullName;
    private final String documentNumber;
    private final String token;
    private final int attempt;
    private final Instant expiresAt;

    public InvitationDeliveryRequested(String invitationId, String email, String fullName,
                                       String documentNumber, String token, int attempt, Instant expiresAt) {
        this.invitationId = invitationId;
        this.email = email;
        this.fullName = fullName;
        this.documentNumber = documentNumber;
        this.token = token;
        this.attempt = attempt;
        this.expiresAt = expiresAt;
    }

    public String invitationId() { return invitationId; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public String documentNumber() { return documentNumber; }
    public String token() { return token; }
    public int attempt() { return attempt; }
    public Instant expiresAt() { return expiresAt; }
}
