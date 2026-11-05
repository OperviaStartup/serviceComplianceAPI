package com.api.servicecompliance.authentication.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.authentication.application.InvitationService;
import com.api.servicecompliance.authentication.application.UserAdministrationService;
import com.api.servicecompliance.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "User management", description = "Invitaciones, acceso y estado de usuarios de la empresa")
public class UserManagementController {
    private final InvitationService invitations;
    private final UserAdministrationService users;
    private final AuthService auth;

    public UserManagementController(InvitationService invitations, UserAdministrationService users,
                                    AuthService auth) {
        this.invitations = invitations;
        this.users = users;
        this.auth = auth;
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Invitar un usuario", description = "Reserva un asiento y envía una activación de un solo uso mediante Resend.")
    @ApiResponse(responseCode = "201", description = "Invitación creada")
    @ApiResponse(responseCode = "403", description = "Administrador o alcance no autorizado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Cuenta, invitación o asiento en conflicto", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public InvitationService.InvitationResponse createInvitation(
            @Valid @RequestBody InvitationService.CreateInvitationRequest request, Principal principal) {
        return invitations.create(request, auth.currentUser(principal.getName()));
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Aceptar una invitación", description = "Crea una contraseña y activa exactamente una cuenta.")
    @ApiResponse(responseCode = "200", description = "Cuenta activada o resultado previamente aceptado")
    @ApiResponse(responseCode = "409", description = "Invitación inválida, vencida o en conflicto", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public InvitationService.AcceptanceResponse acceptInvitation(
            @Valid @RequestBody InvitationService.AcceptInvitationRequest request) {
        return invitations.accept(request);
    }

    @PostMapping("/invitations/{id}/resend")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reenviar una invitación pendiente")
    public InvitationService.InvitationResponse resendInvitation(@PathVariable String id,
                                                                  Principal principal) {
        return invitations.resend(id, auth.currentUser(principal.getName()));
    }

    @DeleteMapping("/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cancelar una invitación pendiente", description = "Libera el asiento reservado.")
    public void cancelInvitation(@PathVariable String id, Principal principal) {
        invitations.cancel(id, auth.currentUser(principal.getName()));
    }

    @PostMapping("/users/{id}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Suspender una cuenta", description = "Revoca sesiones y libera el asiento sin eliminar el historial.")
    public void suspendUser(@PathVariable Long id, Principal principal) {
        users.suspend(id, auth.currentUser(principal.getName()));
    }

    @PostMapping("/users/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reactivar una cuenta", description = "Adquiere un asiento antes de reactivar la cuenta.")
    public UserAdministrationService.UserResponse reactivateUser(@PathVariable Long id,
                                                                  Principal principal) {
        return users.reactivate(id, auth.currentUser(principal.getName()));
    }

    @PatchMapping("/users/{id}/access")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cambiar rol y sede", description = "Solo permite alcances dentro de la empresa administrada.")
    public UserAdministrationService.UserResponse updateAccess(
            @PathVariable Long id, @Valid @RequestBody UserAdministrationService.UpdateAccessRequest request,
            Principal principal) {
        return users.updateAccess(id, request, auth.currentUser(principal.getName()));
    }
}
