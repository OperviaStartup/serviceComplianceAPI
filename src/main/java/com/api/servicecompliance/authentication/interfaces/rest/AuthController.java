package com.api.servicecompliance.authentication.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService){this.authService=authService;}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(@Valid @RequestBody AuthService.RegisterRequest request){return authService.register(request);}
    @PostMapping("/login") public AuthService.AuthResponse login(@Valid @RequestBody AuthService.LoginRequest request){return authService.login(request);}
    @GetMapping("/me") public UserResponse me(Principal principal){var user=authService.currentUser(principal.getName());return new UserResponse(user.getId(),user.getFullName(),user.getEmail(),user.getRole().name());}
    public record UserResponse(Long id,String fullName,String email,String role){}
}
