package com.api.servicecompliance.reporting.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.reporting.domain.OperationalStatus;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class OperationalStatusService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate FIRST_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate LAST_DATE = LocalDate.of(9999, 12, 31);
    private static final Instant MINIMUM_INSTANT = FIRST_DATE.atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final Instant MAXIMUM_INSTANT = LAST_DATE.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    private final OperationalStatusQuery query;
    private final Clock clock;
    private final SecretKeySpec cursorKey;

    public OperationalStatusService(OperationalStatusQuery query, Clock clock,
                                    @Value("${app.jwt.secret}") String signingSecret) {
        this.query = query;
        this.clock = clock;
        this.cursorKey = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public Page list(User actor, Long serviceId, Long siteId, Long operatorId, LocalDate fromDate,
                     LocalDate toDate, OperationalStatus status, String cursor, int limit) {
        if (actor == null || !actor.isActive() || actor.getRole() != Role.SUPERVISOR) {
            throw new AccessDeniedException("Se requiere el rol de supervisor");
        }
        validateId("siteId", siteId);
        validateId("serviceId", serviceId);
        validateId("operatorId", operatorId);
        validateDate("fromDate", fromDate);
        validateDate("toDate", toDate);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw invalid("fromDate", "debe ser anterior o igual a toDate");
        }
        if (limit < 1 || limit > 100) throw invalid("limit", "debe estar entre 1 y 100");

        var filters = new OperationalStatusQuery.Filters(actor.getCompanyId(), actor.getSiteId(), serviceId, operatorId,
                fromDate == null ? null : fromDate.atStartOfDay(LIMA).toInstant(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(LIMA).toInstant(), status);
        String scope = fingerprint(actor, filters, siteId);
        Cursor decoded = cursor == null ? null : decode(cursor, scope);
        Instant asOf = decoded == null ? clock.instant() : decoded.asOf();
        if (siteId != null && !actor.getSiteId().equals(siteId)) {
            return new Page(List.of(), Map.of(OperationalStatus.ASSIGNED, 0L, OperationalStatus.IN_PROGRESS, 0L,
                    OperationalStatus.SUBMITTED, 0L, OperationalStatus.EXCEPTED, 0L, OperationalStatus.OVERDUE, 0L),
                    0, null, false, asOf);
        }

        var result = query.read(filters, decoded == null ? null : decoded.position(), asOf, limit + 1);
        boolean hasMore = result.items().size() > limit;
        var items = hasMore ? List.copyOf(result.items().subList(0, limit)) : List.copyOf(result.items());
        String next = hasMore ? encode(items.get(items.size() - 1), asOf, scope) : null;
        long total = result.counts().values().stream().mapToLong(Long::longValue).sum();
        return new Page(items, result.counts(), total, next, hasMore, asOf);
    }

    private static void validateId(String field, Long value) {
        if (value != null && value < 1) throw invalid(field, "debe ser un identificador positivo");
    }

    private static void validateDate(String field, LocalDate value) {
        if (value != null && (value.isBefore(FIRST_DATE) || value.isAfter(LAST_DATE))) {
            throw invalid(field, "debe estar entre los años 0001 y 9999");
        }
    }

    private static String fingerprint(User actor, OperationalStatusQuery.Filters filters, Long siteId) {
        String value = actor.getId() + "|" + actor.getSessionVersion() + "|" + siteId + "|" + filters.companyId()
                + "|" + filters.siteId() + "|" + filters.serviceId() + "|" + filters.operatorId() + "|"
                + filters.fromInclusive() + "|" + filters.toExclusive() + "|" + filters.status();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String encode(OperationalStatusQuery.Item item, Instant asOf, String fingerprint) {
        Instant sortAt = item.startsAt() == null ? item.dueAt() : item.startsAt();
        String value = "r1|" + fingerprint + "|" + asOf + "|" + sortAt + "|" + item.id();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(signature(value));
    }

    private Cursor decode(String cursor, String fingerprint) {
        if (cursor.length() > 320) throw invalid("cursor", "no tiene un formato válido");
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String[] token = cursor.split("\\.", -1);
            if (token.length != 2) throw invalid("cursor", "no tiene un formato válido");
            String value = new String(decoder.decode(token[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(signature(value), decoder.decode(token[1]))) {
                throw invalid("cursor", "no tiene una firma válida");
            }
            String[] parts = value.split("\\|", -1);
            if (parts.length == 5 && "r1".equals(parts[0]) && fingerprint.equals(parts[1])) {
                Instant asOf = Instant.parse(parts[2]);
                Instant sortAt = Instant.parse(parts[3]);
                long id = Long.parseLong(parts[4]);
                if (id > 0 && !asOf.isBefore(MINIMUM_INSTANT) && asOf.isBefore(MAXIMUM_INSTANT)
                        && !sortAt.isBefore(MINIMUM_INSTANT) && sortAt.isBefore(MAXIMUM_INSTANT)) {
                    return new Cursor(asOf, new OperationalStatusQuery.Position(sortAt, id));
                }
            }
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalid("cursor", "no tiene un formato válido");
        }
        throw invalid("cursor", "es inválido para estos filtros o esta sesión");
    }

    private byte[] signature(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cursorKey);
            return mac.doFinal(("opervia:operational-status:" + value).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static FieldValidationException invalid(String field, String message) {
        return new FieldValidationException("Datos inválidos", Map.of(field, List.of(message)));
    }

    private record Cursor(Instant asOf, OperationalStatusQuery.Position position) {}

    public record Page(List<OperationalStatusQuery.Item> items, Map<OperationalStatus, Long> counts, long total,
                       String nextCursor, boolean hasMore, Instant asOf) {}
}
