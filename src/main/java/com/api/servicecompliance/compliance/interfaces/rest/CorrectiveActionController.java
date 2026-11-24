package com.api.servicecompliance.compliance.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.compliance.application.CorrectiveActionService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Corrective actions", description = "Asignación y consulta de trabajo correctivo")
@SecurityRequirement(name = "bearerAuth")
public class CorrectiveActionController {
    private final CorrectiveActionService actions;
    private final AuthService auth;

    public CorrectiveActionController(CorrectiveActionService actions, AuthService auth) {
        this.actions = actions;
        this.auth = auth;
    }

    @PostMapping("/compliance-cases/{caseId}/corrective-actions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Asignar una acción correctiva",
            description = "Crea trabajo correctivo para el último resultado NON_COMPLIANT del caso.")
    @ApiResponse(responseCode = "201", description = "Acción correctiva asignada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Supervisor u operario asignado fuera de alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Caso inexistente o fuera del alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "El último resultado no es NON_COMPLIANT",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CorrectiveActionService.CorrectiveActionResponse create(
            @PathVariable Long caseId,
            @Valid @RequestBody CorrectiveActionService.CreateCorrectiveActionCommand command,
            Principal principal) {
        return actions.create(caseId, command, auth.currentUser(principal.getName()));
    }

    @GetMapping("/corrective-actions/{actionId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','OPERATOR')")
    @Operation(summary = "Consultar una acción correctiva",
            description = "Devuelve estado persistido, estado operativo derivado, versión e historial.")
    @ApiResponse(responseCode = "200", description = "Acción correctiva autorizada")
    @ApiResponse(responseCode = "403", description = "El operario no es el asignado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Acción inexistente o fuera del alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CorrectiveActionService.CorrectiveActionResponse get(
            @PathVariable Long actionId, Principal principal) {
        return actions.get(actionId, auth.currentUser(principal.getName()));
    }
}
