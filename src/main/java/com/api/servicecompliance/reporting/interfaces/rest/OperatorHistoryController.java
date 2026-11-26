package com.api.servicecompliance.reporting.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.reporting.application.OperatorHistoryQueryService;
import com.api.servicecompliance.reporting.domain.model.HistoryReviewStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/executions")
@Tag(name = "Operator history", description = "Historial persistido de la cuenta operaria autenticada")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('OPERATOR')")
public class OperatorHistoryController {
    private final OperatorHistoryQueryService history;
    private final AuthService auth;

    public OperatorHistoryController(OperatorHistoryQueryService history, AuthService auth) {
        this.history = history;
        this.auth = auth;
    }

    @GetMapping
    @Operation(summary = "Consultar historial personal",
            description = "Ejecuciones enviadas de la cuenta, empresa y sede autenticadas; fechas inclusivas en America/Lima, "
                    + "evaluación independiente y paginación descendente por cursor. No incluye trabajo local pendiente.")
    public OperatorHistoryQueryService.HistoryPage query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) HistoryReviewStatus reviewStatus,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit, Principal principal) {
        return history.query(fromDate, toDate, query, reviewStatus, cursor, limit, auth.currentUser(principal.getName()));
    }
}
