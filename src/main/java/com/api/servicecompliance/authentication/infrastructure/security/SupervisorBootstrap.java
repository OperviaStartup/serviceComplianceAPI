package com.api.servicecompliance.authentication.infrastructure.security;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.shared.domain.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SupervisorBootstrap implements ApplicationRunner {
    private final UserRepository users; private final PasswordEncoder passwordEncoder;
    private final String email; private final String password;
    public SupervisorBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                               @Value("${app.bootstrap.supervisor-email:}") String email,
                               @Value("${app.bootstrap.supervisor-password:}") String password) {
        this.users=users; this.passwordEncoder=passwordEncoder; this.email=email; this.password=password;
    }
    @Override public void run(ApplicationArguments args) {
        if(email.isBlank() && password.isBlank()) return;
        if(email.isBlank() || password.isBlank()) throw new IllegalStateException("Debe configurar ambos datos del supervisor inicial");
        if(password.length()<12) throw new IllegalStateException("La contraseña del supervisor inicial debe tener al menos 12 caracteres");
        if(!users.existsByEmail(email)) users.save(new User("Initial Supervisor",email,passwordEncoder.encode(password),Role.SUPERVISOR));
    }
}
