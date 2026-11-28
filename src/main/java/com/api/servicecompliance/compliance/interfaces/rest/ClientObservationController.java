package com.api.servicecompliance.compliance.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.compliance.application.ClientObservationService;
import com.api.servicecompliance.compliance.application.ComplianceDecisionService;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Client observations", description = "Feedback de cliente y reevaluación de cumplimiento")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPERVISOR')")
public class ClientObservationController {
    private final ClientObservationService observations;
    private final AuthService auth;

    public ClientObservationController(ClientObservationService observations, AuthService auth) {
        this.observations = observations;
        this.auth = auth;
    }

    @PostMapping("/compliance-cases/{caseId}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una observación del cliente")
    @ApiResponse(responseCode = "201", description = "Observación inmutable registrada")
    @ApiResponse(responseCode = "404", description = "Caso inexistente o fuera del alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ClientObservationService.ObservationResponse create(
            @PathVariable Long caseId,
            @Valid @RequestBody ClientObservationService.CreateObservationCommand command,
            Principal principal) {
        return observations.create(caseId, command, auth.currentUser(principal.getName()));
    }

    @GetMapping("/compliance-cases/{caseId}/observations")
    @Operation(summary = "Consultar el historial de observaciones del caso")
    public List<ClientObservationService.ObservationResponse> list(
            @PathVariable Long caseId, Principal principal) {
        return observations.list(caseId, auth.currentUser(principal.getName()));
    }

    @PostMapping("/client-observations/{observationId}/amendments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enmendar una observación mediante una nueva revisión")
    public ClientObservationService.ObservationResponse amend(
            @PathVariable Long observationId,
            @Valid @RequestBody ClientObservationService.AmendmentCommand command,
            Principal principal) {
        return observations.amend(observationId, command, auth.currentUser(principal.getName()));
    }

    @PostMapping("/client-observations/{observationId}/reevaluations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reevaluar el caso desde una revisión de observación",
            description = "Agrega un Compliance Result inmutable usando las reglas de BE-US-11.")
    public ComplianceDecisionService.ComplianceResultResponse reevaluate(
            @PathVariable Long observationId,
            @Valid @RequestBody ComplianceDecisionService.ObservationReevaluationCommand command,
            Principal principal) {
        return observations.reevaluate(observationId, command, auth.currentUser(principal.getName()));
    }
}
