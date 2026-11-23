package com.api.servicecompliance.compliance.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.compliance.application.ComplianceReviewService;
import com.api.servicecompliance.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/compliance-reviews")
@Tag(name = "Compliance reviews", description = "Snapshot consistente de una ejecución enviada")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPERVISOR')")
public class ComplianceReviewController {
    private final ComplianceReviewService reviews;
    private final AuthService auth;

    public ComplianceReviewController(ComplianceReviewService reviews, AuthService auth) {
        this.reviews = reviews;
        this.auth = auth;
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "Revisar una ejecución enviada",
            description = "Devuelve obligación y definición versionadas, ejecución, evidencia, excepciones y "
                    + "faltantes en una lectura REPEATABLE_READ. No crea un resultado de cumplimiento.")
    @ApiResponse(responseCode = "200", description = "Snapshot autorizado de revisión")
    @ApiResponse(responseCode = "403", description = "Se requiere un supervisor activo",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Ejecución inexistente o fuera del alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "La ejecución todavía no fue enviada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ComplianceReviewService.ComplianceReview review(@PathVariable Long executionId,
                                                           Principal principal) {
        return reviews.review(executionId, auth.currentUser(principal.getName()));
    }
}
