package com.api.servicecompliance.reporting.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.reporting.application.OperationalStatusQueryService;
import com.api.servicecompliance.reporting.domain.model.OperationalStatus;
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
@RequestMapping("/api/v1/operational-obligations")
@Tag(name = "Operational status", description = "Proyección operacional autorizada para supervisores")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPERVISOR')")
public class OperationalStatusController {
    private final OperationalStatusQueryService operationalStatus;
    private final AuthService auth;

    public OperationalStatusController(OperationalStatusQueryService operationalStatus, AuthService auth) {
        this.operationalStatus = operationalStatus;
        this.auth = auth;
    }

    @GetMapping
    @Operation(summary = "Consultar el estado operacional",
            description = "Filtra trabajo de la sede del supervisor. Fechas inclusivas en America/Lima; "
                    + "filas y conteos comparten filtros y asOf. El cursor es opaco y ligado a los filtros.")
    @ApiResponse(responseCode = "200", description = "Página operacional y conteos reconciliados")
    @ApiResponse(responseCode = "400", description = "Filtros o cursor inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Rol o sede fuera de alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public OperationalStatusQueryService.OperationalStatusPage query(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(name = "status", required = false) Set<OperationalStatus> statuses,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        return operationalStatus.query(serviceId, siteId, fromDate, toDate, operatorId, statuses,
                cursor, limit, auth.currentUser(principal.getName()));
    }
}
