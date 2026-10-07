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
import java.security.Principal;

@RestController @RequestMapping("/api/v1/obligations")
@Tag(name = "Obligations", description = "Planificación y consulta de obligaciones operativas")
@SecurityRequirement(name = "bearerAuth")
public class ObligationController {
    private final ObligationService obligations; private final AuthService auth;
    public ObligationController(ObligationService obligations,AuthService auth){this.obligations=obligations;this.auth=auth;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Crear obligación", description = "Disponible únicamente para usuarios con rol SUPERVISOR.")
    public Object create(@Valid @RequestBody ObligationService.CreateObligation request){return obligations.create(request);}
    @GetMapping
    @Operation(summary = "Consultar obligaciones asignadas al usuario autenticado")
    public Object assigned(Principal principal){return obligations.findForOperator(auth.currentUser(principal.getName()).getId());}
}
