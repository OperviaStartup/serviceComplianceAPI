package com.api.servicecompliance.serviceplanning.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.serviceplanning.application.ObligationDefinitionService;
import com.api.servicecompliance.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/service-plans/{planId}/obligation-definitions")
@Tag(name = "Obligation definitions", description = "Definiciones operativas y requisitos de evidencia")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class ObligationDefinitionController {
    private final ObligationDefinitionService definitions;
    private final AuthService auth;

    public ObligationDefinitionController(ObligationDefinitionService definitions, AuthService auth) {
        this.definitions = definitions;
        this.auth = auth;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar una definición a un plan en borrador")
    @ApiResponse(responseCode = "201", description = "Definición creada")
    @ApiResponse(responseCode = "400", description = "Regla o metadato inválido", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Rol o sede no autorizados", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "El plan no está en borrador", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ObligationDefinitionService.DefinitionResponse create(
            @PathVariable Long planId, @Valid @RequestBody ObligationDefinitionService.DefinitionRequest request,
            Principal principal) {
        return definitions.create(planId, request, auth.currentUser(principal.getName()));
    }

    @PutMapping("/{definitionId}")
    @Operation(summary = "Editar una definición de un plan en borrador")
    @ApiResponse(responseCode = "200", description = "Definición actualizada")
    @ApiResponse(responseCode = "400", description = "Regla o metadato inválido", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Rol o sede no autorizados", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Plan o definición no encontrados", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Estado o versión en conflicto", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ObligationDefinitionService.DefinitionResponse update(
            @PathVariable Long planId, @PathVariable Long definitionId,
            @Valid @RequestBody ObligationDefinitionService.DefinitionRequest request, Principal principal) {
        return definitions.update(planId, definitionId, request, auth.currentUser(principal.getName()));
    }
}
