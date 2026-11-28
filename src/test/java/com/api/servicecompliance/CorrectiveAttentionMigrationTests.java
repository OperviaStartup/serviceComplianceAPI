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
class CorrectiveAttentionMigrationTests {
    @Test
    void addsImmutableScopedSubmissionsEvidenceAndIdempotency() throws Exception {
        String schema = "corrective_attention_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(flyway(schema, "23").migrate().migrationsExecuted).isEqualTo(23);
        assertThat(flyway(schema, "24").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema, "24").validate();

        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
            long supervisor = user(statement, "Supervisor", "SUPERVISOR");
            long operator = user(statement, "Operator", "OPERATOR");
            Fixture fixture = fixture(statement, supervisor, operator);
            long action = action(statement, fixture, supervisor, operator);
            long otherAction = action(statement, fixture, supervisor, operator);
            long submission = id(statement, submissionSql(action, operator));
            statement.executeUpdate(evidenceSql(submission, action, operator, 1, "image/png"));
            statement.executeUpdate(idempotencySql(operator, submission, "attention-key"));

            rejects(statement, submissionSql(action, operator), "23505");
            rejects(statement, evidenceSql(submission, otherAction, operator, 2, "image/png"), "23503");
            rejects(statement, evidenceSql(submission, action, operator, 6, "image/png"), "23514");
            rejects(statement, evidenceSql(submission, action, operator, 2, "image/gif"), "23514");
            rejects(statement, idempotencySql(operator, submission, "attention-key"), "23505");
            rejects(statement, """
                    INSERT INTO corrective_attention_idempotency(company_id,actor_id,command,idempotency_key,
                            request_hash,submission_id,created_at,expires_at)
                    VALUES (1,%d,'SUBMIT_CORRECTIVE_ATTENTION','expired-key','%s',%d,
                            CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """.formatted(operator, "b".repeat(64), submission), "23514");
        }
    }

    private Fixture fixture(Statement statement, long supervisor, long operator) throws SQLException {
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
                VALUES (%d,%d,1,'NON_COMPLIANT','execution-v0',0,'Decision',%d,CURRENT_TIMESTAMP)
                RETURNING id
                """.formatted(complianceCase, execution, supervisor));
        return new Fixture(complianceCase, result);
    }

    private long action(Statement statement, Fixture fixture, long supervisor, long operator)
            throws SQLException {
        return id(statement, """
                INSERT INTO corrective_actions(compliance_case_id,source_compliance_result_id,company_id,
                        site_id,assignee_id,description,target_date,timezone,state,created_by,created_at,
                        updated_by,updated_at)
                VALUES (%d,%d,1,1,%d,'Replace seal','2031-01-02','America/Lima','ASSIGNED',
                        %d,CURRENT_TIMESTAMP,%d,CURRENT_TIMESTAMP) RETURNING id
                """.formatted(fixture.complianceCase(), fixture.result(), operator, supervisor, supervisor));
    }

    private String submissionSql(long action, long operator) {
        return """
                INSERT INTO corrective_action_submissions(corrective_action_id,submission_number,
                        action_version,details,submitted_by,submitted_at)
                VALUES (%d,1,1,'Correction completed',%d,CURRENT_TIMESTAMP) RETURNING id
                """.formatted(action, operator);
    }

    private String evidenceSql(long submission, long action, long operator, int order, String contentType) {
        return """
                INSERT INTO corrective_action_evidence(submission_id,corrective_action_id,evidence_order,
                        storage_key,content_type,file_size,checksum,captured_at,created_by,created_at)
                VALUES (%d,%d,%d,'company/1/%s.png','%s',10,'%s',CURRENT_TIMESTAMP,%d,CURRENT_TIMESTAMP)
                """.formatted(submission, action, order, UUID.randomUUID(), contentType,
                "a".repeat(64), operator);
    }

    private String idempotencySql(long operator, long submission, String key) {
        return """
                INSERT INTO corrective_attention_idempotency(company_id,actor_id,command,idempotency_key,
                        request_hash,submission_id,created_at,expires_at)
                VALUES (1,%d,'SUBMIT_CORRECTIVE_ATTENTION','%s','%s',%d,CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP + INTERVAL '7 days')
                """.formatted(operator, key, "a".repeat(64), submission);
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
