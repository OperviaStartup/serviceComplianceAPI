package com.api.servicecompliance.authentication.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.infrastructure.security.JwtService;
import com.api.servicecompliance.shared.domain.DomainException;
import com.api.servicecompliance.shared.domain.Role;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Service
public class AuthService {
    private final UserRepository users; private final PasswordEncoder passwordEncoder; private final JwtService jwtService;
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService){this.users=users;this.passwordEncoder=passwordEncoder;this.jwtService=jwtService;}
    public AuthResponse register(RegisterRequest request){
        if(users.existsByEmail(request.email())) throw new DomainException("El correo ya está registrado");
        User user=users.save(new User(request.fullName(),request.email(),passwordEncoder.encode(request.password()),Role.OPERATOR));
        return response(user);
    }
    public AuthResponse login(LoginRequest request){
        User user=users.findByEmail(request.email()).orElseThrow(InvalidCredentialsException::new);
        if(!passwordEncoder.matches(request.password(),user.getPasswordHash())) throw new InvalidCredentialsException();
        return response(user);
    }
    public User currentUser(String email){return users.findByEmail(email).orElseThrow(()->new DomainException("Usuario no encontrado"));}
    private AuthResponse response(User user){return new AuthResponse(jwtService.createToken(user),user.getId(),user.getFullName(),user.getEmail(),user.getRole());}
    public record RegisterRequest(@NotBlank @Size(max=255) String fullName,
                                  @NotBlank @Email @Size(max=255) String email,
                                  @NotBlank @Size(min=8,max=72) String password){}
    public record LoginRequest(@NotBlank @Email String email,@NotBlank String password){}
    public record AuthResponse(String token,Long userId,String fullName,String email,Role role){}
}
