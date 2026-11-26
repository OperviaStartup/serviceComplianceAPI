package com.api.servicecompliance.reporting.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.reporting.domain.model.HistoryReviewStatus;
import com.api.servicecompliance.reporting.domain.model.OperatorExecutionHistory;
import com.api.servicecompliance.reporting.domain.repository.OperatorHistoryProjectionRepository;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OperatorHistoryQueryService {
    private static final ZoneId LIMA = ZoneId.of(Obligation.TIMEZONE);
    private static final String CURSOR_VERSION = "h1";
    private final OperatorHistoryProjectionRepository projection;
    private final Clock clock;

    public OperatorHistoryQueryService(OperatorHistoryProjectionRepository projection, Clock clock) {
        this.projection = projection;
        this.clock = clock;
    }

    public HistoryPage query(LocalDate fromDate, LocalDate toDate, String requestedQuery,
                             HistoryReviewStatus reviewStatus, String cursor, int limit, User actor) {
        if (actor == null || !actor.isActive() || actor.getRole() != Role.OPERATOR
                || actor.getCompanyId() == null || actor.getSiteId() == null)
            throw new AccessDeniedException("Active operator role and scope required");
        if (limit < 1 || limit > 100) throw validation("limit", "debe estar entre 1 y 100");
        String query = requestedQuery == null ? "" : requestedQuery.trim();
        if (query.length() > 200) throw validation("query", "debe contener hasta 200 caracteres");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
            throw validation("fromDate", "debe ser anterior o igual a toDate");
        Instant fromInclusive = boundary(fromDate, false);
        Instant toExclusive = boundary(toDate, true);
        String hash = filterHash(actor, fromDate, toDate, query, reviewStatus);
        Cursor decoded = cursor == null || cursor.isBlank() ? null : decode(cursor, hash);
        Instant now = clock.instant();
        Instant asOf = decoded == null ? now : decoded.asOf();
        if (asOf.isAfter(now)) throw validation("cursor", "contiene un instante futuro");
        var filter = new OperatorHistoryProjectionRepository.HistoryFilter(actor.getCompanyId(), actor.getSiteId(),
                actor.getId(), fromInclusive, toExclusive, query, reviewStatus, asOf);
        var position = decoded == null ? null : new OperatorHistoryProjectionRepository.HistoryPosition(decoded.recordedAt(), decoded.id());
        List<OperatorExecutionHistory> result = projection.query(filter, position, limit);
        boolean hasMore = result.size() > limit;
        List<OperatorExecutionHistory> items = List.copyOf(hasMore ? result.subList(0, limit) : result);
        String next = hasMore ? encode(items.get(items.size() - 1), asOf, hash) : null;
        return new HistoryPage(items, next, hasMore, asOf);
    }

    private static Instant boundary(LocalDate date, boolean nextDay) {
        if (date == null) return null;
        try { return (nextDay ? date.plusDays(1) : date).atStartOfDay(LIMA).toInstant(); }
        catch (DateTimeException exception) { throw validation("toDate", "contiene una fecha fuera de rango"); }
    }

    private static String filterHash(User actor, LocalDate from, LocalDate to, String query, HistoryReviewStatus review) {
        String canonical = actor.getId() + "|" + actor.getCompanyId() + "|" + actor.getSiteId()
                + "|" + from + "|" + to + "|" + query + "|" + review;
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static String encode(OperatorExecutionHistory item, Instant asOf, String hash) {
        String raw = CURSOR_VERSION + "|" + asOf + "|" + item.recordedAt() + "|" + item.id() + "|" + hash;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String cursor, String expectedHash) {
        if (cursor.length() > 2048) throw validation("cursor", "supera el tamaño permitido");
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 5 || !CURSOR_VERSION.equals(parts[0]) || !expectedHash.equals(parts[4]))
                throw validation("cursor", "no corresponde a la cuenta o los filtros solicitados");
            var value = new Cursor(Instant.parse(parts[1]), Instant.parse(parts[2]), Long.parseLong(parts[3]));
            if (value.id() < 1 || value.recordedAt().isAfter(value.asOf()))
                throw validation("cursor", "contiene una posición inválida");
            return value;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw validation("cursor", "no tiene un formato válido");
        }
    }

    private static FieldValidationException validation(String field, String message) {
        return new FieldValidationException("Filtros de historial inválidos", Map.of(field, List.of(message)));
    }

    private record Cursor(Instant asOf, Instant recordedAt, long id) {}
    public record HistoryPage(List<OperatorExecutionHistory> items, String nextCursor, boolean hasMore, Instant asOf) {}
}
