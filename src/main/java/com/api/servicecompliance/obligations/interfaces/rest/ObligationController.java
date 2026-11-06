package com.api.servicecompliance.obligations.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.obligations.application.ObligationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.api.servicecompliance.shared.web.ApiError;
import java.security.Principal;
import java.util.List;

@RestController @RequestMapping("/api/v1/obligations")
@Tag(name = "Obligations", description = "Planificación y consulta de obligaciones operativas")
@SecurityRequirement(name = "bearerAuth")
public class ObligationController {
    private final ObligationService obligations; private final AuthService auth;
    public ObligationController(ObligationService obligations,AuthService auth){this.obligations=obligations;this.auth=auth;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Crear obligación", description = "Disponible para SUPERVISOR o ADMIN dentro de su empresa y sede. Para configurar el detalle de ejecución, envíe startsAt, steps y photoRequirements juntos; dueAt debe ser posterior a startsAt. El orden de las listas determina el orden de presentación.")
    @ApiResponse(responseCode = "201", description = "Obligación creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Rol no autorizado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ObligationService.ObligationDetail create(@Valid @RequestBody ObligationService.CreateObligation request, Principal principal){return obligations.create(request,auth.currentUser(principal.getName()));}
    @GetMapping
    @Operation(summary = "Consultar obligaciones asignadas al usuario autenticado")
    @ApiResponse(responseCode = "401", description = "Token inválido o ausente", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public List<ObligationService.AssignedObligation> assigned(Principal principal){return obligations.findForOperator(auth.currentUser(principal.getName()));}

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    @Operation(summary = "Consultar detalle de una obligación",
            description = "Incluye pasos y requisitos fotográficos ordenados. OPERATOR solo consulta sus asignaciones; SUPERVISOR y ADMIN requieren la misma empresa y sede. Los requisitos describen fotos exigidas, no archivos ya subidos.")
    @ApiResponse(responseCode = "200", description = "Detalle de la obligación")
    @ApiResponse(responseCode = "401", description = "Token inválido o ausente", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "La obligación pertenece a otro operario", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Obligación inexistente o fuera de empresa/sede", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ObligationService.ObligationDetail find(@PathVariable Long id, Principal principal) {
        return obligations.find(id, auth.currentUser(principal.getName()));
    }
}
