package com.api.servicecompliance.authentication.application;

import com.api.servicecompliance.authentication.domain.model.InvitationStatus;
import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.CompanySubscriptionRepository;
import com.api.servicecompliance.authentication.domain.repository.SiteScopeRepository;
import com.api.servicecompliance.authentication.domain.repository.UserInvitationRepository;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.shared.domain.Role;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserAdministrationService {
    private final UserRepository users;
    private final UserInvitationRepository invitations;
    private final CompanySubscriptionRepository subscriptions;
    private final SiteScopeRepository sites;
    private final RefreshService refreshService;

    public UserAdministrationService(UserRepository users, UserInvitationRepository invitations,
                                     CompanySubscriptionRepository subscriptions, SiteScopeRepository sites,
                                     RefreshService refreshService) {
        this.users = users;
        this.invitations = invitations;
        this.subscriptions = subscriptions;
        this.sites = sites;
        this.refreshService = refreshService;
    }

    @Transactional
    public void suspend(Long userId, User actor) {
        requireAdministrator(actor);
        User target = targetInCompany(userId, actor);
        if (target.getId().equals(actor.getId())) {
            throw new ConflictException("SELF_SUSPENSION_FORBIDDEN", "Un administrador no puede suspender su propia cuenta");
        }
        refreshService.suspend(target.getId());
    }

    @Transactional
    public UserResponse reactivate(Long userId, User actor) {
        requireAdministrator(actor);
        var subscription = subscriptions.lockByCompanyId(actor.getCompanyId())
                .orElseThrow(() -> new ConflictException("SUBSCRIPTION_NOT_FOUND", "La empresa no tiene una suscripción"));
        if (!subscription.isActive()) {
            throw new ConflictException("SUBSCRIPTION_INACTIVE", "La suscripción de la empresa no está activa");
        }
        User target = targetInCompanyForUpdate(userId, actor);
        if (target.isActive()) return UserResponse.from(target);
        long occupied = users.countByCompanyIdAndActiveTrue(actor.getCompanyId())
                + invitations.countByCompanyIdAndStatusAndExpiresAtAfter(
                actor.getCompanyId(), InvitationStatus.PENDING, Instant.now());
        if (occupied >= subscription.getSeatLimit()) {
            throw new ConflictException("SEAT_LIMIT_REACHED", "La suscripción no tiene asientos disponibles");
        }
        target.reactivate();
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse updateAccess(Long userId, UpdateAccessRequest request, User actor) {
        requireAdministrator(actor);
        if (!sites.existsByIdAndCompanyId(request.siteId(), actor.getCompanyId())) {
            throw new AccessDeniedException("Site outside administrator company");
        }
        User target = targetInCompanyForUpdate(userId, actor);
        if (target.getId().equals(actor.getId())) {
            throw new ConflictException("SELF_ACCESS_CHANGE_FORBIDDEN", "Un administrador no puede cambiar su propio acceso");
        }
        target.changeAccess(request.role(), request.siteId());
        refreshService.revokeSessions(target.getId());
        return UserResponse.from(target);
    }

    private User targetInCompany(Long userId, User actor) {
        User target = users.findById(userId)
                .orElseThrow(() -> new ConflictException("USER_NOT_FOUND", "El usuario no existe"));
        requireSameCompany(actor, target);
        return target;
    }

    private User targetInCompanyForUpdate(Long userId, User actor) {
        User target = users.lockById(userId)
                .orElseThrow(() -> new ConflictException("USER_NOT_FOUND", "El usuario no existe"));
        requireSameCompany(actor, target);
        return target;
    }

    private void requireAdministrator(User actor) {
        if (actor.getRole() != Role.ADMIN) throw new AccessDeniedException("Administrator required");
    }

    private void requireSameCompany(User actor, User target) {
        if (!actor.getCompanyId().equals(target.getCompanyId())) {
            throw new AccessDeniedException("User outside administrator company");
        }
    }

    public record UpdateAccessRequest(@NotNull Role role, @NotNull Long siteId) {
    }

    public record UserResponse(Long id, String fullName, String email, String documentNumber,
                               Role role, Long companyId, Long siteId, String status) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                    user.getDocumentNumber(), user.getRole(), user.getCompanyId(), user.getSiteId(),
                    user.isActive() ? "ACTIVE" : "SUSPENDED");
        }
    }
}
