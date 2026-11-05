package com.api.servicecompliance.authentication.application;

import com.api.servicecompliance.authentication.domain.repository.UserInvitationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationDeliveryStateService {
    private final UserInvitationRepository invitations;

    public InvitationDeliveryStateService(UserInvitationRepository invitations) {
        this.invitations = invitations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(String invitationId, String emailId) {
        invitations.lockById(invitationId).ifPresent(invitation -> invitation.markSent(emailId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String invitationId) {
        invitations.lockById(invitationId).ifPresent(invitation -> invitation.markDeliveryFailed());
    }
}
