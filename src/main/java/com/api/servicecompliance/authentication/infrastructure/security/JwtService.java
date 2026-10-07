package com.api.servicecompliance.authentication.infrastructure.security;

import com.api.servicecompliance.authentication.domain.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key; private final long expirationMs;
    public JwtService(@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.expiration-ms}") long expirationMs){
        if(secret.length()<32) throw new IllegalArgumentException("JWT_SECRET debe tener al menos 32 caracteres");
        key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.expirationMs=expirationMs;
    }
    public String createToken(User user){return Jwts.builder().subject(user.getEmail()).claim("role",user.getRole().name()).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+expirationMs)).signWith(key).compact();}
    public String subject(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}
    public String role(String token){return (String) Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().get("role");}
}
