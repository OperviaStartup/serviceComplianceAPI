package com.api.servicecompliance.obligations.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.obligations.application.AssignedObligationQueryService;
import com.api.servicecompliance.obligations.domain.model.ObligationStatus;
import com.api.servicecompliance.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/assigned-obligations")
@Tag(name = "Assigned obligations", description = "Trabajo actual y sincronización incremental del operario")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('OPERATOR')")
public class AssignedObligationController {
    private final AssignedObligationQueryService assignedObligations;
    private final AuthService auth;

    public AssignedObligationController(AssignedObligationQueryService assignedObligations, AuthService auth) {
        this.assignedObligations = assignedObligations;
        this.auth = auth;
    }

    @GetMapping
    @Operation(summary = "Listar obligaciones actualmente asignadas",
            description = "Fechas inclusivas en America/Lima. El cursor es opaco y solo puede reutilizarse con los mismos filtros.")
    @ApiResponse(responseCode = "200", description = "Página estable de obligaciones")
    @ApiResponse(responseCode = "400", description = "Filtros o cursor inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Solo disponible para operadores",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AssignedObligationQueryService.AssignedObligationPage list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(name = "status", required = false) Set<ObligationStatus> statuses,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        return assignedObligations.list(fromDate, toDate, statuses, cursor, limit,
                auth.currentUser(principal.getName()));
    }

    @GetMapping("/changes")
    @Operation(summary = "Sincronizar cambios de asignación y estado",
            description = "Devuelve UPSERT o REMOVED en orden de secuencia. Inicie con el syncCursor de una lista completa.")
    @ApiResponse(responseCode = "200", description = "Cambios posteriores al cursor")
    @ApiResponse(responseCode = "400", description = "Cursor inválido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Solo disponible para operadores",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AssignedObligationQueryService.AssignedObligationChangePage changes(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        return assignedObligations.changes(cursor, limit, auth.currentUser(principal.getName()));
    }
}
