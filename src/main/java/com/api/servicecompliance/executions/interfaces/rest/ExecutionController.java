package com.api.servicecompliance.executions.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.executions.application.ExecutionService;
import com.api.servicecompliance.executions.application.EvidenceService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api/v1/executions")
public class ExecutionController {
    private final ExecutionService executions; private final EvidenceService evidence; private final AuthService auth;
    public ExecutionController(ExecutionService executions,EvidenceService evidence,AuthService auth){this.executions=executions;this.evidence=evidence;this.auth=auth;}
    @PostMapping public Object register(@RequestBody ExecutionService.RegisterExecution request,Principal principal){return executions.register(request,auth.currentUser(principal.getName()).getId());}
    @GetMapping("/{id}") public Object find(@PathVariable Long id){return executions.find(id);}
    @PostMapping("/{id}/evidence") public Object addEvidence(@PathVariable Long id,@RequestBody EvidenceService.EvidenceRequest request,Principal principal){return evidence.add(id,request,auth.currentUser(principal.getName()).getId());}
    @GetMapping("/{id}/evidence") public Object findEvidence(@PathVariable Long id){return evidence.findByExecution(id);}
}
