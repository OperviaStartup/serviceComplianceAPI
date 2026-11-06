package com.api.servicecompliance.serviceplanning.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.serviceplanning.application.ServicePlanService;
import com.api.servicecompliance.serviceplanning.application.ServicePlanActivationService;
import com.api.servicecompliance.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/service-plans")
@Tag(name = "Service plans", description = "Planes de servicio por empresa, servicio y sede")
@SecurityRequirement(name = "bearerAuth")
public class ServicePlanController {
    private final ServicePlanService servicePlans;
    private final AuthService auth;
    private final ServicePlanActivationService activation;

    public ServicePlanController(ServicePlanService servicePlans, AuthService auth,
                                 ServicePlanActivationService activation) {
        this.servicePlans = servicePlans;
        this.auth = auth;
        this.activation = activation;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un plan de servicio en borrador",
            description = "Usa fechas inclusivas en America/Lima y requiere Idempotency-Key.")
    @ApiResponse(responseCode = "201", description = "Plan creado o creación original reproducida")
    @ApiResponse(responseCode = "400", description = "Campos o periodo inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido o ausente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Administrador, empresa, servicio o sede no autorizados",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "La clave de idempotencia tiene otro contenido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<ServicePlanService.ServicePlanResponse> create(
            @Valid @RequestBody ServicePlanService.CreateServicePlanRequest request,
            @Parameter(required = true, description = "Clave de 8 a 100 caracteres, única por creación y administrador")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal) {
        var response = servicePlans.create(request, idempotencyKey, auth.currentUser(principal.getName()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/service-plans/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar un plan de la empresa autenticada")
    @ApiResponse(responseCode = "200", description = "Plan encontrado")
    @ApiResponse(responseCode = "401", description = "Token inválido o ausente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "El rol no puede consultar planes",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Plan inexistente o de otra empresa",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ServicePlanService.ServicePlanResponse find(@PathVariable Long id, Principal principal) {
        return servicePlans.find(id, auth.currentUser(principal.getName()));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar una versión de plan válida")
    @ApiResponse(responseCode = "200", description = "Versión activa o activación original reproducida")
    @ApiResponse(responseCode = "400", description = "Versión esperada ausente", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Administrador no autorizado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Plan no encontrado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Versión, requisitos, estado o periodo en conflicto",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ServicePlanActivationService.PlanVersionResponse activate(
            @PathVariable Long id, @Valid @RequestBody ServicePlanActivationService.VersionCommand command,
            Principal principal) {
        return activation.activate(id, command, auth.currentUser(principal.getName()));
    }

    @PostMapping("/{id}/amendments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Clonar una versión activa como nueva enmienda en borrador")
    @ApiResponse(responseCode = "201", description = "Nueva versión en borrador creada")
    @ApiResponse(responseCode = "400", description = "Versión esperada ausente", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Administrador no autorizado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Plan no encontrado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Versión o estado en conflicto",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ServicePlanActivationService.PlanVersionResponse amend(
            @PathVariable Long id, @Valid @RequestBody ServicePlanActivationService.VersionCommand command,
            Principal principal) {
        return activation.amend(id, command, auth.currentUser(principal.getName()));
    }
}
