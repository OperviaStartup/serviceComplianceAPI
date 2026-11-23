package com.api.servicecompliance.reporting.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.reporting.application.OperationalStatusService;
import com.api.servicecompliance.reporting.domain.OperationalStatus;
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

@RestController
@RequestMapping("/api/v1/operational-status")
@Tag(name = "Operational status", description = "Consulta operativa de supervisión")
@SecurityRequirement(name = "bearerAuth")
public class OperationalStatusController {
    private final OperationalStatusService statusService;
    private final AuthService auth;

    public OperationalStatusController(OperationalStatusService statusService, AuthService auth) {
        this.statusService = statusService;
        this.auth = auth;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Consultar estado operativo de obligaciones asignadas",
            description = "Filtros opcionales. Las fechas son inclusivas en America/Lima. "
                    + "Los totales incluyen todas las filas que coinciden con los filtros en la empresa y sede del supervisor.")
    @ApiResponse(responseCode = "200", description = "Página de obligaciones y totales del filtro")
    @ApiResponse(responseCode = "400", description = "Filtros o cursor inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido o ausente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Rol no autorizado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public OperationalStatusService.Page list(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) OperationalStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        return statusService.list(auth.currentUser(principal.getName()), serviceId, siteId, operatorId,
                fromDate, toDate, status, cursor, limit);
    }
}
