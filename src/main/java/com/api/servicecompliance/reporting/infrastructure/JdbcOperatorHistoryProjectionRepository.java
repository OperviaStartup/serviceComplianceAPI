package com.api.servicecompliance.reporting.infrastructure;

import com.api.servicecompliance.compliance.domain.model.ComplianceOutcome;
import com.api.servicecompliance.executions.domain.model.ExecutionResult;
import com.api.servicecompliance.executions.domain.model.ExecutionState;
import com.api.servicecompliance.executions.domain.model.SyncOutcome;
import com.api.servicecompliance.reporting.domain.model.OperatorExecutionHistory;
import com.api.servicecompliance.reporting.domain.repository.OperatorHistoryProjectionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcOperatorHistoryProjectionRepository implements OperatorHistoryProjectionRepository {
    private static final String PROJECTION = """
            WITH history_projection AS (
                SELECT execution.id, execution.obligation_id, obligation.title AS activity,
                       obligation.site_name, COALESCE(obligation.timezone, 'America/Lima') AS timezone,
                       execution.state, execution.result, execution.executed_at, execution.submitted_at,
                       execution.notes, COALESCE(execution.submitted_at, execution.executed_at) AS recorded_at,
                       decision.id AS result_id, decision.compliance_case_id, decision.outcome,
                       decision.decision_version, decision.decided_at, decision.explanation,
                       CASE WHEN decision.id IS NULL THEN 'PENDING_REVIEW' ELSE CAST(decision.outcome AS VARCHAR(30)) END AS review_status
                FROM executions execution
                JOIN obligations obligation ON obligation.id = execution.obligation_id
                LEFT JOIN compliance_cases compliance_case
                  ON compliance_case.obligation_id = obligation.id
                 AND compliance_case.company_id = obligation.company_id
                 AND compliance_case.site_id = obligation.site_id
                LEFT JOIN compliance_results decision
                  ON decision.compliance_case_id = compliance_case.id
                 AND decision.execution_id = execution.id
                 AND decision.decided_at <= :asOf
                 AND NOT EXISTS (
                     SELECT 1 FROM compliance_results newer
                     WHERE newer.compliance_case_id = compliance_case.id
                       AND newer.execution_id = execution.id AND newer.decided_at <= :asOf
                       AND newer.decision_version > decision.decision_version
                 )
                WHERE execution.operator_id = :operatorId
                  AND obligation.company_id = :companyId AND obligation.site_id = :siteId
                  AND execution.state IN ('SUBMITTED', 'LEGACY') AND execution.result IS NOT NULL
                  AND COALESCE(execution.submitted_at, execution.executed_at) <= :asOf
            )
            SELECT * FROM history_projection WHERE 1 = 1
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOperatorHistoryProjectionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<OperatorExecutionHistory> query(HistoryFilter filter, HistoryPosition position, int limit) {
        var parameters = new MapSqlParameterSource()
                .addValue("companyId", filter.companyId()).addValue("siteId", filter.siteId())
                .addValue("operatorId", filter.operatorId()).addValue("asOf", Timestamp.from(filter.asOf()))
                .addValue("pageSize", limit + 1);
        StringBuilder sql = new StringBuilder(PROJECTION);
        if (filter.fromInclusive() != null) {
            sql.append(" AND recorded_at >= :fromInclusive");
            parameters.addValue("fromInclusive", Timestamp.from(filter.fromInclusive()));
        }
        if (filter.toExclusive() != null) {
            sql.append(" AND recorded_at < :toExclusive");
            parameters.addValue("toExclusive", Timestamp.from(filter.toExclusive()));
        }
        if (!filter.query().isEmpty()) {
            sql.append(" AND LOWER(activity) LIKE LOWER(:search) ESCAPE '!'");
            String literal = filter.query().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            parameters.addValue("search", "%" + literal + "%");
        }
        if (filter.reviewStatus() != null) {
            sql.append(" AND review_status = :reviewStatus");
            parameters.addValue("reviewStatus", filter.reviewStatus().name());
        }
        if (position != null) {
            sql.append(" AND (recorded_at < :cursorTime OR (recorded_at = :cursorTime AND id < :cursorId))");
            parameters.addValue("cursorTime", Timestamp.from(position.recordedAt())).addValue("cursorId", position.executionId());
        }
        sql.append(" ORDER BY recorded_at DESC, id DESC LIMIT :pageSize");
        return jdbc.query(sql.toString(), parameters, this::map);
    }

    private OperatorExecutionHistory map(ResultSet result, int row) throws SQLException {
        Long decisionId = result.getObject("result_id", Long.class);
        var decision = decisionId == null ? null : new OperatorExecutionHistory.ComplianceResultView(
                decisionId, result.getLong("compliance_case_id"), ComplianceOutcome.valueOf(result.getString("outcome")),
                result.getLong("decision_version"), instant(result, "decided_at"), result.getString("explanation"));
        return new OperatorExecutionHistory(result.getLong("id"), result.getLong("obligation_id"),
                result.getString("activity"), result.getString("site_name"), result.getString("timezone"),
                ExecutionState.valueOf(result.getString("state")), ExecutionResult.valueOf(result.getString("result")),
                instant(result, "executed_at"), instant(result, "submitted_at"), result.getString("notes"),
                SyncOutcome.SYNCED, decision);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
