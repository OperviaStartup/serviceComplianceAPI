package com.api.servicecompliance.authentication.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.api.servicecompliance.shared.web.ApiError;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registro, inicio de sesión y perfil de usuario")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService){this.authService=authService;}
    @PostMapping("/register")
    @Operation(summary = "Registro público deshabilitado", description = "El registro público está deshabilitado. La incorporación por invitación pertenece a BE-US-20.", deprecated = true)
    @ApiResponse(responseCode = "403", description = "Registro público deshabilitado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthService.AuthResponse register(@RequestBody AuthService.RegisterRequest request){return authService.register(request);}
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión")
    @ApiResponse(responseCode = "200", description = "Token de acceso JWT y token de renovación opaco")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthService.AuthResponse login(@Valid @RequestBody AuthService.LoginRequest request){return authService.login(request);}
    @PostMapping("/refresh")
    @Operation(summary = "Renovar sesión", description = "Rota el token de renovación de un solo uso; su reutilización revoca la familia.")
    @ApiResponse(responseCode = "200", description = "Nuevos tokens de acceso y renovación")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token inválido, vencido o revocado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuthService.AuthResponse refresh(@Valid @RequestBody AuthService.RefreshRequest request){return authService.refresh(request.refreshToken());}
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar sesión", description = "Revoca la familia del token de renovación; el token de acceso sigue vigente hasta su vencimiento.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada o token desconocido")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void logout(@Valid @RequestBody AuthService.RefreshRequest request){authService.logout(request.refreshToken());}
    @GetMapping("/me")
    @Operation(summary = "Consultar perfil actual")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public UserResponse me(Principal principal){var user=authService.currentUser(principal.getName());return new UserResponse(user.getId(),user.getFullName(),user.getEmail(),user.getDocumentNumber(),user.getRole().name());}
    public record UserResponse(Long id,String fullName,String email,String documentNumber,String role){}
}
