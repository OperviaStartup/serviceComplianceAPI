package com.api.servicecompliance.obligations.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.obligations.application.ObligationGenerationService;
import com.api.servicecompliance.shared.web.ApiError;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/service-plans/{planId}/obligations")
@Tag(name = "Obligation generation", description = "Generación de obligaciones desde planes activos")
@SecurityRequirement(name = "bearerAuth")
public class ObligationGenerationController {
    private final ObligationGenerationService generation;
    private final AuthService auth;
    public ObligationGenerationController(ObligationGenerationService generation, AuthService auth) {
        this.generation = generation;
        this.auth = auth;
    }
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponse(responseCode = "200", description = "Obligaciones generadas; repetir el rango es seguro")
    @ApiResponse(responseCode = "400", description = "Rango inválido", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Plan no activo", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ObligationGenerationService.GenerationResponse generate(
            @PathVariable Long planId, @Valid @RequestBody ObligationGenerationService.GenerationRequest request,
            Principal principal) {
        return generation.generate(planId, request, auth.currentUser(principal.getName()));
    }
}
