package com.api.servicecompliance.authentication.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registro, inicio de sesión y perfil de usuario")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService){this.authService=authService;}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar usuario", description = "Registra un usuario como OPERATOR. El rol SUPERVISOR no se asigna desde este endpoint.")
    @ApiResponse(responseCode = "201", description = "Usuario registrado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o correo duplicado")
    public AuthService.AuthResponse register(@Valid @RequestBody AuthService.RegisterRequest request){return authService.register(request);}
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión")
    @ApiResponse(responseCode = "200", description = "Token JWT generado")
    @ApiResponse(responseCode = "400", description = "Credenciales inválidas")
    public AuthService.AuthResponse login(@Valid @RequestBody AuthService.LoginRequest request){return authService.login(request);}
    @GetMapping("/me")
    @Operation(summary = "Consultar perfil actual")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public UserResponse me(Principal principal){var user=authService.currentUser(principal.getName());return new UserResponse(user.getId(),user.getFullName(),user.getEmail(),user.getRole().name());}
    public record UserResponse(Long id,String fullName,String email,String role){}
}
