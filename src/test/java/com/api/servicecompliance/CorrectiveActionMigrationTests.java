package com.api.servicecompliance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "FIELD_OPERATIONS_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/field_operations_test")
class CorrectiveActionMigrationTests {
    @Test
    void addsScopedActionsAndAnImmutableValidatedTransitionHistory() throws Exception {
        String schema = "corrective_action_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(flyway(schema, "22").migrate().migrationsExecuted).isEqualTo(22);
        assertThat(flyway(schema, "23").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema, "23").validate();

        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
            long supervisor = user(statement, "Supervisor", "SUPERVISOR");
            long operator = user(statement, "Operator", "OPERATOR");
            Fixture first = fixture(statement, supervisor, operator, "NON_COMPLIANT");
            Fixture second = fixture(statement, supervisor, operator, "NON_COMPLIANT");
            long action = id(statement, actionSql(first.complianceCase(), first.result(), supervisor, operator));
            statement.executeUpdate(eventSql(action, 0, "NULL", "ASSIGNED", supervisor));
            statement.executeUpdate(eventSql(action, 1, "'ASSIGNED'", "ATTENTION_SUBMITTED", operator));

            rejects(statement, actionSql(second.complianceCase(), first.result(), supervisor, operator), "23503");
            rejects(statement, eventSql(action, 1, "'ASSIGNED'", "ATTENTION_SUBMITTED", operator), "23505");
            rejects(statement, eventSql(action, 2, "'ASSIGNED'", "CLOSED", supervisor), "23514");
            rejects(statement, "UPDATE corrective_actions SET state='CLOSED' WHERE id=" + action, "23514");
            rejects(statement, "UPDATE corrective_actions SET timezone='UTC' WHERE id=" + action, "23514");
        }
    }

    private Fixture fixture(Statement statement, long supervisor, long operator, String outcome)
            throws SQLException {
        long obligation = id(statement, """
                INSERT INTO obligations(title,description,site_name,assigned_operator_id,status,due_at,
                                        company_id,site_id)
                VALUES ('Fixture','Fixture','Site',%d,'SUBMITTED','2031-01-01T15:30:00Z',1,1)
                RETURNING id
                """.formatted(operator));
        long execution = id(statement, """
                INSERT INTO executions(obligation_id,operator_id,result,executed_at,state,started_at,
                                       submitted_at,updated_at)
                VALUES (%d,%d,'COMPLETED','2031-01-01T15:00:00Z','SUBMITTED',
                        '2031-01-01T14:00:00Z','2031-01-01T15:00:00Z','2031-01-01T15:00:00Z')
                RETURNING id
                """.formatted(obligation, operator));
        long complianceCase = id(statement, """
                INSERT INTO compliance_cases(obligation_id,company_id,site_id,decision_version,created_by,
                                             created_at,updated_at)
                VALUES (%d,1,1,1,%d,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id
                """.formatted(obligation, supervisor));
        long result = id(statement, """
                INSERT INTO compliance_results(compliance_case_id,execution_id,decision_version,outcome,
                                               source_review_version,source_execution_version,explanation,
                                               decided_by,decided_at)
                VALUES (%d,%d,1,'%s','execution-v0',0,'Decision',%d,CURRENT_TIMESTAMP)
                RETURNING id
                """.formatted(complianceCase, execution, outcome, supervisor));
        return new Fixture(complianceCase, result);
    }

    private String actionSql(long complianceCase, long result, long supervisor, long operator) {
        return """
                INSERT INTO corrective_actions(compliance_case_id,source_compliance_result_id,company_id,
                                               site_id,assignee_id,description,target_date,timezone,state,
                                               created_by,created_at,updated_by,updated_at)
                VALUES (%d,%d,1,1,%d,'Replace seal','2031-01-02','America/Lima','ASSIGNED',
                        %d,CURRENT_TIMESTAMP,%d,CURRENT_TIMESTAMP) RETURNING id
                """.formatted(complianceCase, result, operator, supervisor, supervisor);
    }

    private String eventSql(long action, long version, String fromState, String toState, long actor) {
        return """
                INSERT INTO corrective_action_events(corrective_action_id,action_version,from_state,to_state,
                                                     actor_id,reason,occurred_at)
                VALUES (%d,%d,%s,'%s',%d,'Lifecycle event',CURRENT_TIMESTAMP)
                """.formatted(action, version, fromState, toState, actor);
    }

    private long user(Statement statement, String name, String role) throws SQLException {
        return id(statement, """
                INSERT INTO users(full_name,email,password_hash,role,created_at,company_id,site_id)
                VALUES ('%s','%s-%s@example.test','not-a-password','%s',CURRENT_TIMESTAMP,1,1)
                RETURNING id
                """.formatted(name, name.toLowerCase(), UUID.randomUUID(), role));
    }

    private void rejects(Statement statement, String sql, String state) {
        assertThatThrownBy(() -> statement.executeUpdate(sql)).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(state));
    }

    private long id(Statement statement, String sql) throws SQLException {
        try (var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(url(), user(), password()).schemas(schema)
                .locations("classpath:db/migration").target(target).load();
    }

    private Connection connection(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(url(), user(), password());
        connection.setSchema(schema);
        return connection;
    }

    private String url() { return System.getenv("FIELD_OPERATIONS_TEST_JDBC_URL"); }
    private String user() { return System.getenv("FIELD_OPERATIONS_TEST_DB_USER"); }
    private String password() { return System.getenv().getOrDefault("FIELD_OPERATIONS_TEST_DB_PASSWORD", ""); }

    private record Fixture(long complianceCase, long result) {}
}
