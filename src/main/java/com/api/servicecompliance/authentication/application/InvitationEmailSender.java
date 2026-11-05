package com.api.servicecompliance.authentication.application;

public interface InvitationEmailSender {
    String send(InvitationDeliveryRequested delivery);
}
