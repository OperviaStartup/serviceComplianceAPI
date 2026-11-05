package com.api.servicecompliance.authentication.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.infrastructure.security.JwtService;
import com.api.servicecompliance.shared.domain.DomainException;
import com.api.servicecompliance.shared.domain.Role;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshService refreshService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshService refreshService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshService = refreshService;
    }

    public AuthResponse register(RegisterRequest request) {
        throw new AccessDeniedException("Registration disabled");
    }

    public AuthResponse login(LoginRequest request){
        String identifier = request.identifier().trim();
        User user = (identifier.matches("\\d{8}")
                ? users.findByDocumentNumber(identifier)
                : users.findByEmailIgnoreCase(identifier))
                .orElseThrow(InvalidCredentialsException::new);
        boolean validPassword = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!validPassword || !user.isActive()) throw new InvalidCredentialsException();
        return response(user);
    }

    public User currentUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> new DomainException("Usuario no encontrado"));
    }

    public AuthResponse refresh(String token) {
        var rotation = refreshService.rotate(token);
        if (rotation == null) throw new InvalidCredentialsException();
        return response(rotation.user(), rotation.token());
    }

    public void logout(String token) {
        refreshService.logout(token);
    }

    private AuthResponse response(User user) {
        return response(user, refreshService.start(user));
    }

    private AuthResponse response(User user, String refreshToken) {
        return new AuthResponse(jwtService.createToken(user), user.getId(), user.getFullName(), user.getEmail(),
                user.getDocumentNumber(), user.getRole(), refreshToken);
    }

    public record RegisterRequest(@NotBlank @Size(max=255) String fullName,
                                  @NotBlank @Email @Size(max=255) String email,
                                  @NotBlank @Size(min=8,max=72) String password){}
    public record LoginRequest(@NotBlank @Size(max=255) @JsonAlias("email") String identifier,
                               @NotBlank String password){}
    public record AuthResponse(String token,Long userId,String fullName,String email,String documentNumber,
                               Role role,String refreshToken){}
    public record RefreshRequest(@NotBlank String refreshToken){}
}
