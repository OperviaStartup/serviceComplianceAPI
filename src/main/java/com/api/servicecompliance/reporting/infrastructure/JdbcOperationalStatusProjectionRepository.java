package com.api.servicecompliance.reporting.infrastructure;

import com.api.servicecompliance.reporting.domain.model.OperationalObligation;
import com.api.servicecompliance.reporting.domain.model.OperationalStatus;
import com.api.servicecompliance.reporting.domain.repository.OperationalStatusProjectionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcOperationalStatusProjectionRepository implements OperationalStatusProjectionRepository {
    private static final String PROJECTION = """
            WITH operational_projection AS (
                SELECT obligation.id AS obligation_id,
                       plan.service_id,
                       obligation.site_id,
                       obligation.site_name,
                       obligation.assigned_operator_id AS operator_id,
                       operator.full_name AS operator_name,
                       obligation.title AS activity,
                       obligation.starts_at,
                       obligation.due_at,
                       COALESCE(obligation.timezone, 'America/Lima') AS timezone,
                       obligation.source_plan_id,
                       obligation.source_plan_version,
                       COALESCE(obligation.starts_at, obligation.due_at) AS sort_at,
                       CASE
                           WHEN EXISTS (
                               SELECT 1
                               FROM executions execution
                               JOIN operational_exceptions exception
                                 ON exception.execution_id = execution.id
                               WHERE execution.obligation_id = obligation.id
                           ) THEN 'EXCEPTED'
                           WHEN obligation.status = 'SUBMITTED' THEN 'SUBMITTED'
                           WHEN obligation.starts_at IS NOT NULL
                                AND (obligation.status = 'OVERDUE' OR obligation.due_at <= :asOf)
                               THEN 'OVERDUE'
                           WHEN obligation.status = 'IN_PROGRESS' THEN 'IN_PROGRESS'
                           ELSE 'ASSIGNED'
                       END AS operational_status
                FROM obligations obligation
                LEFT JOIN service_plans plan
                  ON plan.id = obligation.source_plan_id
                 AND plan.company_id = obligation.company_id
                JOIN users operator
                  ON operator.id = obligation.assigned_operator_id
                 AND operator.company_id = obligation.company_id
                 AND operator.site_id = obligation.site_id
                WHERE obligation.company_id = :companyId
                  AND obligation.site_id = :siteId
                  AND obligation.assigned_operator_id IS NOT NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOperationalStatusProjectionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ProjectionResult query(ProjectionFilter filter, ProjectionPosition position, int limit) {
        MapSqlParameterSource parameters = parameters(filter).addValue("pageSize", limit + 1);
        String cte = projection(filter);
        String statusPredicate = " operational_status IN (:statuses)";
        String cursorPredicate = position == null ? "" : """
                 AND (sort_at > :cursorTime OR (sort_at = :cursorTime AND obligation_id > :cursorId))
                """;
        if (position != null) {
            parameters.addValue("cursorTime", Timestamp.from(position.sortAt()));
            parameters.addValue("cursorId", position.obligationId());
        }

        List<OperationalObligation> items = jdbc.query(cte + """
                SELECT * FROM operational_projection
                WHERE""" + statusPredicate + cursorPredicate + """
                ORDER BY sort_at, obligation_id
                LIMIT :pageSize
                """, parameters, this::map);

        EnumMap<OperationalStatus, Long> counts = new EnumMap<>(OperationalStatus.class);
        for (OperationalStatus status : OperationalStatus.values()) counts.put(status, 0L);
        jdbc.query(cte + """
                SELECT operational_status, COUNT(*) AS status_count
                FROM operational_projection
                WHERE""" + statusPredicate + """
                GROUP BY operational_status
                """, parameters, (RowCallbackHandler) result -> counts.put(
                OperationalStatus.valueOf(result.getString("operational_status")), result.getLong("status_count")));
        return new ProjectionResult(items, counts);
    }

    private String projection(ProjectionFilter filter) {
        StringBuilder sql = new StringBuilder(PROJECTION);
        if (filter.serviceId() != null) sql.append(" AND plan.service_id = :serviceId\n");
        if (filter.operatorId() != null) sql.append(" AND obligation.assigned_operator_id = :operatorId\n");
        if (filter.fromInclusive() != null)
            sql.append(" AND COALESCE(obligation.starts_at, obligation.due_at) >= :fromInclusive\n");
        if (filter.toExclusive() != null)
            sql.append(" AND COALESCE(obligation.starts_at, obligation.due_at) < :toExclusive\n");
        return sql.append(")\n").toString();
    }

    private MapSqlParameterSource parameters(ProjectionFilter filter) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("companyId", filter.companyId())
                .addValue("siteId", filter.siteId())
                .addValue("asOf", Timestamp.from(filter.asOf()))
                .addValue("statuses", filter.statuses().stream().map(Enum::name).toList());
        if (filter.serviceId() != null) parameters.addValue("serviceId", filter.serviceId());
        if (filter.operatorId() != null) parameters.addValue("operatorId", filter.operatorId());
        if (filter.fromInclusive() != null) parameters.addValue("fromInclusive", Timestamp.from(filter.fromInclusive()));
        if (filter.toExclusive() != null) parameters.addValue("toExclusive", Timestamp.from(filter.toExclusive()));
        return parameters;
    }

    private OperationalObligation map(ResultSet result, int row) throws SQLException {
        return new OperationalObligation(result.getLong("obligation_id"), nullableLong(result, "service_id"),
                result.getLong("site_id"), result.getString("site_name"), result.getLong("operator_id"),
                result.getString("operator_name"), result.getString("activity"),
                instant(result, "starts_at"), instant(result, "due_at"), result.getString("timezone"),
                nullableLong(result, "source_plan_id"), nullableInteger(result, "source_plan_version"),
                OperationalStatus.valueOf(result.getString("operational_status")), instant(result, "sort_at"));
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
