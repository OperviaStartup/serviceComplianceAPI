package com.api.servicecompliance.reporting.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.reporting.domain.model.OperationalObligation;
import com.api.servicecompliance.reporting.domain.model.OperationalStatus;
import com.api.servicecompliance.reporting.domain.repository.OperationalStatusProjectionRepository;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OperationalStatusQueryService {
    private static final ZoneId LIMA = ZoneId.of(Obligation.TIMEZONE);
    private static final String CURSOR_VERSION = "o1";
    private final OperationalStatusProjectionRepository projection;
    private final Clock clock;

    public OperationalStatusQueryService(OperationalStatusProjectionRepository projection, Clock clock) {
        this.projection = projection;
        this.clock = clock;
    }

    public OperationalStatusPage query(Long serviceId, Long siteId, LocalDate fromDate, LocalDate toDate,
                                       Long operatorId, Set<OperationalStatus> requestedStatuses,
                                       String cursor, int limit, User actor) {
        requireSupervisor(actor);
        validate(serviceId, siteId, fromDate, toDate, operatorId, limit, actor);
        Set<OperationalStatus> statuses = requestedStatuses == null || requestedStatuses.isEmpty()
                ? EnumSet.allOf(OperationalStatus.class) : EnumSet.copyOf(requestedStatuses);
        Long effectiveSite = siteId == null ? actor.getSiteId() : siteId;
        String filterHash = filterHash(actor, serviceId, effectiveSite, fromDate, toDate, operatorId, statuses);
        Cursor decoded = cursor == null || cursor.isBlank() ? null : decode(cursor, filterHash);
        Instant now = clock.instant();
        Instant asOf = decoded == null ? now : decoded.asOf();
        if (asOf.isAfter(now)) throw validation("cursor", "contiene un instante futuro");
        var filter = new OperationalStatusProjectionRepository.ProjectionFilter(actor.getCompanyId(), effectiveSite,
                serviceId, operatorId, fromDate == null ? null : fromDate.atStartOfDay(LIMA).toInstant(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(LIMA).toInstant(), statuses, asOf);
        var position = decoded == null ? null
                : new OperationalStatusProjectionRepository.ProjectionPosition(decoded.sortAt(), decoded.obligationId());
        var result = projection.query(filter, position, limit);
        boolean hasMore = result.items().size() > limit;
        List<OperationalObligation> items = hasMore ? result.items().subList(0, limit) : result.items();
        String nextCursor = hasMore && !items.isEmpty()
                ? encode(items.get(items.size() - 1), asOf, filterHash) : null;
        long total = result.counts().values().stream().mapToLong(Long::longValue).sum();
        return new OperationalStatusPage(items, result.counts(), total, nextCursor, hasMore, asOf);
    }

    private static void requireSupervisor(User actor) {
        if (actor == null || !actor.isActive() || actor.getRole() != Role.SUPERVISOR)
            throw new AccessDeniedException("Supervisor role required");
    }

    private static void validate(Long serviceId, Long siteId, LocalDate fromDate, LocalDate toDate,
                                 Long operatorId, int limit, User actor) {
        if (serviceId != null && serviceId < 1) throw validation("serviceId", "debe ser positivo");
        if (operatorId != null && operatorId < 1) throw validation("operatorId", "debe ser positivo");
        if (siteId != null && !siteId.equals(actor.getSiteId()))
            throw new AccessDeniedException("Site is outside supervisor scope");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
            throw validation("fromDate", "debe ser anterior o igual a toDate");
        if (limit < 1 || limit > 100) throw validation("limit", "debe estar entre 1 y 100");
    }

    private static String filterHash(User actor, Long serviceId, Long siteId, LocalDate fromDate,
                                     LocalDate toDate, Long operatorId, Set<OperationalStatus> statuses) {
        String canonical = actor.getId() + "|" + actor.getCompanyId() + "|" + siteId + "|" + serviceId
                + "|" + fromDate + "|" + toDate + "|" + operatorId + "|"
                + statuses.stream().sorted(Comparator.comparing(Enum::name)).map(Enum::name).toList();
        try {
            byte[] value = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String encode(OperationalObligation item, Instant asOf, String filterHash) {
        String raw = CURSOR_VERSION + "|" + asOf.getEpochSecond() + "|" + asOf.getNano() + "|"
                + item.sortAt().getEpochSecond() + "|" + item.sortAt().getNano() + "|"
                + item.obligationId() + "|" + filterHash;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String cursor, String expectedHash) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 7 || !CURSOR_VERSION.equals(parts[0]) || !expectedHash.equals(parts[6]))
                throw validation("cursor", "no corresponde a los filtros solicitados");
            return new Cursor(Instant.ofEpochSecond(Long.parseLong(parts[1]), Integer.parseInt(parts[2])),
                    Instant.ofEpochSecond(Long.parseLong(parts[3]), Integer.parseInt(parts[4])),
                    Long.parseLong(parts[5]));
        } catch (IllegalArgumentException exception) {
            throw validation("cursor", "no tiene un formato válido");
        }
    }

    private static FieldValidationException validation(String field, String message) {
        return new FieldValidationException("Filtros de estado operacional inválidos", Map.of(field, List.of(message)));
    }

    private record Cursor(Instant asOf, Instant sortAt, Long obligationId) {
    }

    public record OperationalStatusPage(List<OperationalObligation> items,
                                        Map<OperationalStatus, Long> counts,
                                        long total, String nextCursor, boolean hasMore, Instant asOf) {
    }
}
