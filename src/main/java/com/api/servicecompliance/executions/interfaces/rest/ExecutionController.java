package com.api.servicecompliance.executions.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.executions.application.ExecutionService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api/v1/executions")
public class ExecutionController {
    private final ExecutionService executions; private final AuthService auth;
    public ExecutionController(ExecutionService executions,AuthService auth){this.executions=executions;this.auth=auth;}
    @PostMapping public Object register(@RequestBody ExecutionService.RegisterExecution request,Principal principal){return executions.register(request,auth.currentUser(principal.getName()).getId());}
    @GetMapping("/{id}") public Object find(@PathVariable Long id){return executions.find(id);}
}
