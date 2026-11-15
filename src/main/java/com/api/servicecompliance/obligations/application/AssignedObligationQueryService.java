package com.api.servicecompliance.obligations.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.model.ObligationAssignmentChange;
import com.api.servicecompliance.obligations.domain.model.ObligationAssignmentChangeType;
import com.api.servicecompliance.obligations.domain.model.ObligationStatus;
import com.api.servicecompliance.obligations.domain.repository.ObligationAssignmentChangeRepository;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.Role;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AssignedObligationQueryService {
    private static final ZoneId LIMA = ZoneId.of(Obligation.TIMEZONE);
    private static final Instant MINIMUM_FILTER_TIME = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAXIMUM_FILTER_TIME = Instant.parse("9999-12-31T23:59:59Z");
    private static final String SNAPSHOT_CURSOR_VERSION = "s1";
    private static final String CHANGE_CURSOR_VERSION = "c1";

    private final ObligationRepository obligations;
    private final ObligationAssignmentChangeRepository changes;
    private final Clock clock;

    public AssignedObligationQueryService(ObligationRepository obligations,
                                          ObligationAssignmentChangeRepository changes,
                                          Clock clock) {
        this.obligations = obligations;
        this.changes = changes;
        this.clock = clock;
    }

    public AssignedObligationPage list(LocalDate fromDate, LocalDate toDate, Set<ObligationStatus> requestedStatuses,
                                       String cursor, int limit, User actor) {
        requireOperator(actor);
        validateLimit(limit);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw validation("fromDate", "debe ser anterior o igual a toDate");
        }

        Set<ObligationStatus> statuses = requestedStatuses == null || requestedStatuses.isEmpty()
                ? EnumSet.allOf(ObligationStatus.class)
                : EnumSet.copyOf(requestedStatuses);
        String filterHash = filterHash(fromDate, toDate, statuses);
        SnapshotCursor decoded = cursor == null || cursor.isBlank() ? null : decodeSnapshot(cursor, filterHash);
        long highWatermark = decoded == null
                ? changes.findHighWatermark(actor.getCompanyId(), actor.getSiteId(), actor.getId())
                : decoded.syncHighWatermark();
        Instant now = clock.instant();
        Instant fromInclusive = fromDate == null ? MINIMUM_FILTER_TIME : fromDate.atStartOfDay(LIMA).toInstant();
        Instant toExclusive = toDate == null ? MAXIMUM_FILTER_TIME : toDate.plusDays(1).atStartOfDay(LIMA).toInstant();
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Obligation> matches = decoded == null
                ? obligations.findAssignedPage(actor.getCompanyId(), actor.getSiteId(), actor.getId(),
                        fromInclusive, toExclusive, statuses, statuses.contains(ObligationStatus.OVERDUE), now, page)
                : obligations.findAssignedPageAfter(actor.getCompanyId(), actor.getSiteId(), actor.getId(),
                        fromInclusive, toExclusive, statuses, statuses.contains(ObligationStatus.OVERDUE), now,
                        decoded.sortAt(), decoded.obligationId(), page);
        boolean hasMore = matches.size() > limit;
        List<Obligation> selected = hasMore ? matches.subList(0, limit) : matches;
        String nextCursor = hasMore && !selected.isEmpty()
                ? encodeSnapshot(selected.get(selected.size() - 1), highWatermark, filterHash)
                : null;
        return new AssignedObligationPage(selected.stream().map(item -> view(item, now)).toList(), nextCursor,
                encodeChange(highWatermark), hasMore);
    }

    public AssignedObligationChangePage changes(String cursor, int limit, User actor) {
        requireOperator(actor);
        validateLimit(limit);
        long afterId = cursor == null || cursor.isBlank() ? 0 : decodeChange(cursor);
        List<ObligationAssignmentChange> matches = changes
                .findByCompanyIdAndSiteIdAndOperatorIdAndIdGreaterThanOrderByIdAsc(
                        actor.getCompanyId(), actor.getSiteId(), actor.getId(), afterId,
                        PageRequest.of(0, limit + 1));
        boolean hasMore = matches.size() > limit;
        List<ObligationAssignmentChange> selected = hasMore ? matches.subList(0, limit) : matches;
        Map<Long, Obligation> current = new HashMap<>();
        obligations.findAllById(selected.stream().map(ObligationAssignmentChange::getObligationId).distinct().toList())
                .forEach(obligation -> current.put(obligation.getId(), obligation));
        Instant now = clock.instant();
        List<AssignedObligationChangeView> result = new ArrayList<>();
        for (ObligationAssignmentChange change : selected) {
            Obligation obligation = current.get(change.getObligationId());
            boolean stillAssigned = obligation != null
                    && actor.getId().equals(obligation.getAssignedOperatorId())
                    && actor.getCompanyId().equals(obligation.getCompanyId())
                    && actor.getSiteId().equals(obligation.getSiteId());
            ObligationAssignmentChangeType type = change.getChangeType() == ObligationAssignmentChangeType.UPSERT
                    && stillAssigned ? ObligationAssignmentChangeType.UPSERT : ObligationAssignmentChangeType.REMOVED;
            result.add(new AssignedObligationChangeView(encodeChange(change.getId()), type,
                    change.getObligationId(), type == ObligationAssignmentChangeType.UPSERT ? view(obligation, now) : null,
                    change.getChangedAt()));
        }
        long nextId = selected.isEmpty() ? afterId : selected.get(selected.size() - 1).getId();
        return new AssignedObligationChangePage(result, encodeChange(nextId), hasMore);
    }

    private static AssignedObligationView view(Obligation obligation, Instant now) {
        return new AssignedObligationView(obligation.getId(), obligation.getCompanyId(), obligation.getSiteId(),
                obligation.getSiteName(), obligation.getTitle(), obligation.getDescription(),
                obligation.getStartsAt(), obligation.getDueAt(), obligation.getTimezone(),
                obligation.getOccurrenceDate(), obligation.getSourcePlanId(), obligation.getSourcePlanVersion(),
                obligation.getSourceDefinitionId(), obligation.getSourceDefinitionVersion(),
                obligation.statusAt(now));
    }

    private static void requireOperator(User actor) {
        if (actor == null || !actor.isActive() || actor.getRole() != Role.OPERATOR) {
            throw new AccessDeniedException("Operator role required");
        }
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw validation("limit", "debe estar entre 1 y 100");
        }
    }

    private static FieldValidationException validation(String field, String message) {
        return new FieldValidationException("Datos inválidos", Map.of(field, List.of(message)));
    }

    private static String filterHash(LocalDate fromDate, LocalDate toDate, Set<ObligationStatus> statuses) {
        String canonical = (fromDate == null ? "-" : fromDate) + "|"
                + (toDate == null ? "-" : toDate) + "|"
                + statuses.stream().sorted(Comparator.comparing(Enum::name)).map(Enum::name).toList();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String encodeSnapshot(Obligation obligation, long highWatermark, String filterHash) {
        Instant sortAt = obligation.getSortAt();
        String raw = SNAPSHOT_CURSOR_VERSION + "|" + sortAt.getEpochSecond() + "|" + sortAt.getNano()
                + "|" + obligation.getId() + "|" + highWatermark + "|" + filterHash;
        return encode(raw);
    }

    private static SnapshotCursor decodeSnapshot(String cursor, String expectedFilterHash) {
        try {
            String[] parts = decode(cursor).split("\\|", -1);
            if (parts.length != 6 || !SNAPSHOT_CURSOR_VERSION.equals(parts[0])
                    || !expectedFilterHash.equals(parts[5])) {
                throw validation("cursor", "no corresponde a los filtros solicitados");
            }
            return new SnapshotCursor(Instant.ofEpochSecond(Long.parseLong(parts[1]), Integer.parseInt(parts[2])),
                    Long.parseLong(parts[3]), Long.parseLong(parts[4]));
        } catch (IllegalArgumentException exception) {
            throw validation("cursor", "no tiene un formato válido");
        }
    }

    private static String encodeChange(long id) {
        return encode(CHANGE_CURSOR_VERSION + "|" + id);
    }

    private static long decodeChange(String cursor) {
        try {
            String[] parts = decode(cursor).split("\\|", -1);
            if (parts.length != 2 || !CHANGE_CURSOR_VERSION.equals(parts[0])) {
                throw validation("cursor", "no tiene un formato válido");
            }
            long id = Long.parseLong(parts[1]);
            if (id < 0) throw validation("cursor", "no tiene un formato válido");
            return id;
        } catch (IllegalArgumentException exception) {
            throw validation("cursor", "no tiene un formato válido");
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private record SnapshotCursor(Instant sortAt, Long obligationId, long syncHighWatermark) {}

    public record AssignedObligationView(Long id, Long companyId, Long siteId, String siteName,
                                         String activity, String instructions, Instant startsAt, Instant dueAt,
                                         String timezone, LocalDate occurrenceDate, Long sourcePlanId,
                                         Integer sourcePlanVersion, Long sourceDefinitionId,
                                         Long sourceDefinitionVersion, ObligationStatus status) {}

    public record AssignedObligationPage(List<AssignedObligationView> items, String nextCursor,
                                         String syncCursor, boolean hasMore) {}

    public record AssignedObligationChangeView(String cursor, ObligationAssignmentChangeType type,
                                               Long obligationId, AssignedObligationView obligation,
                                               Instant changedAt) {}

    public record AssignedObligationChangePage(List<AssignedObligationChangeView> changes,
                                               String nextCursor, boolean hasMore) {}
}
