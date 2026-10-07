package com.api.servicecompliance.executions.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.executions.application.ExecutionService;
import com.api.servicecompliance.executions.application.EvidenceService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController @RequestMapping("/api/v1/executions")
@Tag(name = "Executions and Evidence", description = "Registro de ejecuciones y evidencias en campo")
@SecurityRequirement(name = "bearerAuth")
public class ExecutionController {
    private final ExecutionService executions; private final EvidenceService evidence; private final AuthService auth;
    public ExecutionController(ExecutionService executions,EvidenceService evidence,AuthService auth){this.executions=executions;this.evidence=evidence;this.auth=auth;}
    @PostMapping
    @Operation(summary = "Registrar ejecución", description = "El operario autenticado solo puede registrar una ejecución sobre una obligación que le fue asignada.")
    public Object register(@Valid @RequestBody ExecutionService.RegisterExecution request,Principal principal){return executions.register(request,auth.currentUser(principal.getName()).getId());}
    @GetMapping("/{id}")
    @Operation(summary = "Consultar ejecución")
    public Object find(@PathVariable Long id){return executions.find(id);}
    @PostMapping("/{id}/evidence")
    @Operation(summary = "Adjuntar evidencia", description = "Registra los metadatos de una evidencia asociada a una ejecución.")
    public Object addEvidence(@PathVariable Long id,@Valid @RequestBody EvidenceService.EvidenceRequest request,Principal principal){return evidence.add(id,request,auth.currentUser(principal.getName()).getId());}
    @GetMapping("/{id}/evidence")
    @Operation(summary = "Consultar evidencias de una ejecución")
    public Object findEvidence(@PathVariable Long id){return evidence.findByExecution(id);}
}
