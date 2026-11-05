package com.api.servicecompliance.authentication.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshSession;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshFamily;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshFamilyRepository;
import com.api.servicecompliance.authentication.infrastructure.persistence.RefreshSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshService {
    private final RefreshSessionRepository sessions;
    private final UserRepository users;
    private final RefreshFamilyRepository families;
    private final SecureRandom random = new SecureRandom();
    public RefreshService(RefreshSessionRepository sessions, UserRepository users, RefreshFamilyRepository families) {
        this.sessions = sessions;
        this.users = users;
        this.families = families;
    }

    @Transactional
    public String start(User user) {
        var current = users.lockById(user.getId()).orElseThrow(InvalidCredentialsException::new);
        if (!current.isActive()) throw new InvalidCredentialsException();
        String family = UUID.randomUUID().toString();
        families.save(new RefreshFamily(family));
        return issue(current.getId(), family);
    }

    @Transactional
    public Rotation rotate(String token) {
        String digest = hash(token);
        Long userId = sessions.findUserByHash(digest).orElse(null);
        if (userId == null) return null;

        var user = users.lockById(userId).orElse(null);
        var family = sessions.findFamilyByHash(digest).orElse(null);
        if (family == null) return null;
        families.lockById(family);
        var session = sessions.lockByHash(digest).orElse(null);
        if (session == null) return null;
        if (session.isUsed()) {
            sessions.revokeFamily(session.getFamilyId());
            return null;
        }

        if (session.isRevoked() || !session.getExpiresAt().isAfter(Instant.now()) || user == null || !user.isActive()) {
            sessions.revokeFamily(session.getFamilyId());
            return null;
        }
        session.markUsed();
        return new Rotation(user, issue(user.getId(), session.getFamilyId()));
    }

    @Transactional
    public void logout(String token) {
        var family = sessions.findFamilyByHash(hash(token)).orElse(null);
        if (family == null) return;
        families.lockById(family);
        sessions.revokeFamily(family);
    }

    @Transactional
    public void suspend(Long userId) {
        var user = users.lockById(userId).orElseThrow();
        user.suspend();
        sessions.revokeUser(userId);
    }

    private String issue(Long userId, String family) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new RefreshSession(userId, family, hash(token), Instant.now().plus(7, ChronoUnit.DAYS)));
        return token;
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Rotation(User user, String token) {}
}
