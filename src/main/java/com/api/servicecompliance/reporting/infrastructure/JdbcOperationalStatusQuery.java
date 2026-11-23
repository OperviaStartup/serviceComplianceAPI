package com.api.servicecompliance.reporting.infrastructure;

import com.api.servicecompliance.reporting.application.OperationalStatusQuery;
import com.api.servicecompliance.reporting.domain.OperationalStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;

@Repository
class JdbcOperationalStatusQuery implements OperationalStatusQuery {
    private final JdbcClient jdbc;

    JdbcOperationalStatusQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Result read(Filters filters, Position after, Instant asOf, int fetchSize) {
        StringBuilder sql = new StringBuilder("""
                WITH classified AS (
                    SELECT f.*, CASE
                        WHEN exception_count > 0 THEN 'EXCEPTED'
                        WHEN execution_state = 'SUBMITTED' THEN 'SUBMITTED'
                        WHEN execution_state = 'IN_PROGRESS' THEN 'IN_PROGRESS'
                        WHEN starts_at IS NOT NULL AND due_at <= :asOf THEN 'OVERDUE'
                        ELSE 'ASSIGNED'
                    END AS operational_status
                    FROM operational_status_facts_v1 f
                    WHERE company_id = :companyId AND site_id = :siteId
                """);
        if (filters.serviceId() != null) sql.append(" AND service_id = :serviceId");
        if (filters.operatorId() != null) sql.append(" AND assigned_operator_id = :operatorId");
        if (filters.fromInclusive() != null) sql.append(" AND sort_at >= :fromInclusive");
        if (filters.toExclusive() != null) sql.append(" AND sort_at < :toExclusive");

        sql.append("), filtered AS (SELECT * FROM classified");
        if (filters.status() != null) sql.append(" WHERE operational_status = :status");

        sql.append("""
                ), totals AS (
                    SELECT COUNT(CASE WHEN operational_status = 'ASSIGNED' THEN 1 END) AS assigned_count,
                           COUNT(CASE WHEN operational_status = 'IN_PROGRESS' THEN 1 END) AS in_progress_count,
                           COUNT(CASE WHEN operational_status = 'SUBMITTED' THEN 1 END) AS submitted_count,
                           COUNT(CASE WHEN operational_status = 'EXCEPTED' THEN 1 END) AS excepted_count,
                           COUNT(CASE WHEN operational_status = 'OVERDUE' THEN 1 END) AS overdue_count
                    FROM filtered
                ), page AS (
                    SELECT * FROM filtered
                """);
        if (after != null) {
            sql.append(" WHERE sort_at > :sortAt OR (sort_at = :sortAt AND id > :afterId)");
        }
        sql.append("""
                    ORDER BY sort_at, id
                    LIMIT :fetchSize
                )
                SELECT page.*, totals.* FROM totals LEFT JOIN page ON TRUE
                ORDER BY page.sort_at, page.id
                """);

        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString())
                .param("asOf", asOf.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC))
                .param("companyId", filters.companyId())
                .param("siteId", filters.siteId())
                .param("fetchSize", fetchSize);
        if (filters.serviceId() != null) statement.param("serviceId", filters.serviceId());
        if (filters.operatorId() != null) statement.param("operatorId", filters.operatorId());
        if (filters.fromInclusive() != null) {
            statement.param("fromInclusive", filters.fromInclusive().atOffset(ZoneOffset.UTC));
        }
        if (filters.toExclusive() != null) {
            statement.param("toExclusive", filters.toExclusive().atOffset(ZoneOffset.UTC));
        }
        if (filters.status() != null) statement.param("status", filters.status().name());
        if (after != null) {
            statement.param("sortAt", after.sortAt().atOffset(ZoneOffset.UTC)).param("afterId", after.id());
        }
        return statement.query(JdbcOperationalStatusQuery::readResult);
    }

    private static Result readResult(ResultSet rows) throws SQLException {
        rows.next(); // The aggregate always returns one row, even if the page is empty.
        var counts = new EnumMap<OperationalStatus, Long>(OperationalStatus.class);
        for (OperationalStatus status : OperationalStatus.values()) {
            counts.put(status, rows.getLong(status.name().toLowerCase(Locale.ROOT) + "_count"));
        }

        var items = new ArrayList<Item>();
        do {
            Long id = rows.getObject("id", Long.class);
            if (id != null) {
                items.add(new Item(id, rows.getObject("service_id", Long.class), rows.getLong("site_id"),
                        rows.getString("site_name"), rows.getLong("assigned_operator_id"), rows.getString("title"),
                        instant(rows, "starts_at"), instant(rows, "due_at"), rows.getString("timezone"),
                        rows.getObject("source_plan_id", Long.class), rows.getObject("source_plan_version", Integer.class),
                        OperationalStatus.valueOf(rows.getString("operational_status"))));
            }
        } while (rows.next());
        return new Result(items, counts);
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime timestamp = rows.getObject(column, OffsetDateTime.class);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
