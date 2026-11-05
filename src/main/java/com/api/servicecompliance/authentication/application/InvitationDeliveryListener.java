package com.api.servicecompliance.authentication.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InvitationDeliveryListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationDeliveryListener.class);

    private final InvitationEmailSender emailSender;
    private final InvitationDeliveryStateService deliveryState;

    public InvitationDeliveryListener(InvitationEmailSender emailSender,
                                      InvitationDeliveryStateService deliveryState) {
        this.emailSender = emailSender;
        this.deliveryState = deliveryState;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(InvitationDeliveryRequested delivery) {
        try {
            String emailId = emailSender.send(delivery);
            deliveryState.markSent(delivery.invitationId(), emailId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Invitation delivery failed for invitation {}", delivery.invitationId());
            deliveryState.markFailed(delivery.invitationId());
        }
    }
}
