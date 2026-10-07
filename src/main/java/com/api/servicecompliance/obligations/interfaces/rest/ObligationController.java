package com.api.servicecompliance.obligations.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.obligations.application.ObligationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.security.Principal;

@RestController @RequestMapping("/api/v1/obligations")
public class ObligationController {
    private final ObligationService obligations; private final AuthService auth;
    public ObligationController(ObligationService obligations,AuthService auth){this.obligations=obligations;this.auth=auth;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SUPERVISOR')") public Object create(@Valid @RequestBody ObligationService.CreateObligation request){return obligations.create(request);}
    @GetMapping public Object assigned(Principal principal){return obligations.findForOperator(auth.currentUser(principal.getName()).getId());}
}
