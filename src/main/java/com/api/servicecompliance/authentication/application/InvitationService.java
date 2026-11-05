package com.api.servicecompliance.authentication.application;

import com.api.servicecompliance.authentication.domain.model.InvitationStatus;
import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.model.UserInvitation;
import com.api.servicecompliance.authentication.domain.repository.CompanySubscriptionRepository;
import com.api.servicecompliance.authentication.domain.repository.SiteScopeRepository;
import com.api.servicecompliance.authentication.domain.repository.UserInvitationRepository;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.shared.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class InvitationService {
    private final UserInvitationRepository invitations;
    private final UserRepository users;
    private final CompanySubscriptionRepository subscriptions;
    private final SiteScopeRepository sites;
    private final PasswordEncoder passwordEncoder;
    private final InvitationTokenService tokenService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final long expirationHours;

    public InvitationService(UserInvitationRepository invitations, UserRepository users,
                             CompanySubscriptionRepository subscriptions, SiteScopeRepository sites,
                             PasswordEncoder passwordEncoder, InvitationTokenService tokenService,
                             ApplicationEventPublisher events,
                             @Value("${app.invitation.expiration-hours:72}") long expirationHours) {
        this.invitations = invitations;
        this.users = users;
        this.subscriptions = subscriptions;
        this.sites = sites;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.events = events;
        this.clock = Clock.systemUTC();
        this.expirationHours = expirationHours;
    }

    @Transactional
    public InvitationResponse create(CreateInvitationRequest request, User actor) {
        requireAdministrator(actor);
        Instant now = clock.instant();
        var subscription = lockActiveSubscription(actor.getCompanyId());
        validateSite(request.siteId(), actor.getCompanyId());

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String documentNumber = request.documentNumber().trim();
        rejectExistingAccount(email, documentNumber);
        rejectLiveInvitation(actor.getCompanyId(), email, documentNumber, now);
        requireAvailableSeat(actor.getCompanyId(), subscription.getSeatLimit(), now);

        var token = tokenService.generate();
        var invitation = invitations.save(new UserInvitation(actor.getCompanyId(), request.siteId(), email,
                documentNumber, request.fullName().trim(), request.role(), token.hash(),
                now.plus(expirationHours, ChronoUnit.HOURS), actor.getId()));
        publishDelivery(invitation, token.value());
        return InvitationResponse.from(invitation);
    }

    @Transactional
    public AcceptanceResponse accept(AcceptInvitationRequest request) {
        String tokenHash = tokenService.hash(request.token());
        var snapshot = invitations.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ConflictException("INVITATION_INVALID", "La invitación no es válida"));
        var subscription = lockActiveSubscription(snapshot.getCompanyId());
        var invitation = invitations.lockByTokenHash(tokenHash)
                .orElseThrow(() -> new ConflictException("INVITATION_INVALID", "La invitación no es válida"));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            User accepted = users.findById(invitation.getAcceptedUserId())
                    .orElseThrow(() -> new IllegalStateException("Accepted invitation has no account"));
            return AcceptanceResponse.from(accepted);
        }

        Instant now = clock.instant();
        if (!invitation.isLiveAt(now)) {
            if (invitation.getStatus() == InvitationStatus.PENDING) invitation.expire();
            throw new ConflictException("INVITATION_EXPIRED", "La invitación venció o fue cancelada");
        }

        rejectExistingAccount(invitation.getEmail(), invitation.getDocumentNumber());
        long occupiedSeats = occupiedSeats(invitation.getCompanyId(), now);
        if (occupiedSeats > subscription.getSeatLimit()) {
            throw new ConflictException("SEAT_LIMIT_REACHED", "La suscripción no tiene asientos disponibles");
        }

        User user = users.save(new User(invitation.getFullName(), invitation.getEmail(),
                invitation.getDocumentNumber(), passwordEncoder.encode(request.password()), invitation.getRole(),
                invitation.getCompanyId(), invitation.getSiteId()));
        invitation.accept(user.getId(), now);
        return AcceptanceResponse.from(user);
    }

    @Transactional
    public InvitationResponse resend(String invitationId, User actor) {
        requireAdministrator(actor);
        lockActiveSubscription(actor.getCompanyId());
        UserInvitation invitation = invitations.lockById(invitationId)
                .orElseThrow(() -> new ConflictException("INVITATION_NOT_FOUND", "La invitación no existe"));
        requireSameCompany(actor, invitation);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("INVITATION_NOT_PENDING", "La invitación ya no está pendiente");
        }
        var token = tokenService.generate();
        invitation.prepareRedelivery(token.hash(), clock.instant().plus(expirationHours, ChronoUnit.HOURS));
        publishDelivery(invitation, token.value());
        return InvitationResponse.from(invitation);
    }

    @Transactional
    public void cancel(String invitationId, User actor) {
        requireAdministrator(actor);
        subscriptions.lockByCompanyId(actor.getCompanyId())
                .orElseThrow(() -> new ConflictException("SUBSCRIPTION_NOT_FOUND", "La empresa no tiene una suscripción"));
        UserInvitation invitation = invitations.lockById(invitationId)
                .orElseThrow(() -> new ConflictException("INVITATION_NOT_FOUND", "La invitación no existe"));
        requireSameCompany(actor, invitation);
        if (invitation.getStatus() == InvitationStatus.PENDING) invitation.cancel();
    }

    private com.api.servicecompliance.authentication.domain.model.CompanySubscription lockActiveSubscription(Long companyId) {
        var subscription = subscriptions.lockByCompanyId(companyId)
                .orElseThrow(() -> new ConflictException("SUBSCRIPTION_NOT_FOUND", "La empresa no tiene una suscripción"));
        if (!subscription.isActive()) {
            throw new ConflictException("SUBSCRIPTION_INACTIVE", "La suscripción de la empresa no está activa");
        }
        return subscription;
    }

    private void requireAvailableSeat(Long companyId, int seatLimit, Instant now) {
        if (occupiedSeats(companyId, now) >= seatLimit) {
            throw new ConflictException("SEAT_LIMIT_REACHED", "La suscripción no tiene asientos disponibles");
        }
    }

    private long occupiedSeats(Long companyId, Instant now) {
        return users.countByCompanyIdAndActiveTrue(companyId)
                + invitations.countByCompanyIdAndStatusAndExpiresAtAfter(companyId, InvitationStatus.PENDING, now);
    }

    private void rejectExistingAccount(String email, String documentNumber) {
        if (users.existsByEmailIgnoreCase(email) || users.existsByDocumentNumber(documentNumber)) {
            throw new ConflictException("ACCOUNT_ALREADY_EXISTS", "Ya existe una cuenta para la identidad indicada");
        }
    }

    private void rejectLiveInvitation(Long companyId, String email, String documentNumber, Instant now) {
        boolean duplicatedEmail = invitations.existsByCompanyIdAndEmailIgnoreCaseAndStatusAndExpiresAtAfter(
                companyId, email, InvitationStatus.PENDING, now);
        boolean duplicatedDocument = invitations.existsByCompanyIdAndDocumentNumberAndStatusAndExpiresAtAfter(
                companyId, documentNumber, InvitationStatus.PENDING, now);
        if (duplicatedEmail || duplicatedDocument) {
            throw new ConflictException("INVITATION_ALREADY_PENDING", "Ya existe una invitación pendiente");
        }
    }

    private void validateSite(Long siteId, Long companyId) {
        if (!sites.existsByIdAndCompanyId(siteId, companyId)) {
            throw new AccessDeniedException("Site outside administrator company");
        }
    }

    private void publishDelivery(UserInvitation invitation, String token) {
        events.publishEvent(new InvitationDeliveryRequested(invitation.getId(), invitation.getEmail(),
                invitation.getFullName(), invitation.getDocumentNumber(), token,
                invitation.getDeliveryAttempt(), invitation.getExpiresAt()));
    }

    private void requireAdministrator(User actor) {
        if (actor.getRole() != Role.ADMIN) throw new AccessDeniedException("Administrator required");
    }

    private void requireSameCompany(User actor, UserInvitation invitation) {
        if (!actor.getCompanyId().equals(invitation.getCompanyId())) {
            throw new AccessDeniedException("Invitation outside administrator company");
        }
    }

    public record CreateInvitationRequest(
            @NotBlank @Size(max = 255) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "\\d{8}") String documentNumber,
            @NotNull Role role,
            @NotNull Long siteId) {
    }

    public record AcceptInvitationRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record InvitationResponse(String id, String email, String documentNumber, Role role,
                                     Long companyId, Long siteId, String status,
                                     String deliveryStatus, Instant expiresAt) {
        static InvitationResponse from(UserInvitation invitation) {
            return new InvitationResponse(invitation.getId(), invitation.getEmail(),
                    invitation.getDocumentNumber(), invitation.getRole(), invitation.getCompanyId(),
                    invitation.getSiteId(), invitation.getStatus().name(),
                    invitation.getDeliveryStatus().name(), invitation.getExpiresAt());
        }
    }

    public record AcceptanceResponse(Long userId, String email, String documentNumber, String status) {
        static AcceptanceResponse from(User user) {
            return new AcceptanceResponse(user.getId(), user.getEmail(), user.getDocumentNumber(),
                    user.isActive() ? "ACTIVE" : "SUSPENDED");
        }
    }
}
