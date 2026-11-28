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
class ClientObservationMigrationTests {
    @Test
    void addsAppendOnlyObservationRevisionsAndSingleResultLink() throws Exception {
        String schema = "client_observation_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(flyway(schema, "24").migrate().migrationsExecuted).isEqualTo(24);
        assertThat(flyway(schema, "25").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema, "25").validate();

        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
            long supervisor = user(statement);
            Fixture fixture = fixture(statement, supervisor);
            UUID series = UUID.randomUUID();
            long first = id(statement, observationSql(fixture.complianceCase(), supervisor,
                    series, 1, null, null));
            long second = id(statement, observationSql(fixture.complianceCase(), supervisor,
                    series, 2, first, "Corrected wording"));

            statement.executeUpdate("UPDATE compliance_results SET source_observation_id=" + second
                    + " WHERE id=" + fixture.result());
            rejects(statement, "UPDATE compliance_results SET source_observation_id=" + second
                    + " WHERE id=" + fixture.secondResult(), "23505");
            rejects(statement, observationSql(fixture.complianceCase(), supervisor,
                    series, 2, first, "Duplicate revision"), "23505");
            rejects(statement, observationSql(fixture.complianceCase(), supervisor,
                    UUID.randomUUID(), 1, null, "Invalid first revision"), "23514");
        }
    }

    private Fixture fixture(Statement statement, long supervisor) throws SQLException {
        long obligation = id(statement, """
                INSERT INTO obligations(title,description,site_name,assigned_operator_id,status,due_at,
                                        company_id,site_id)
                VALUES ('Fixture','Fixture','Site',%d,'SUBMITTED','2031-01-01T15:30:00Z',1,1)
                RETURNING id
                """.formatted(supervisor));
        long execution = id(statement, """
                INSERT INTO executions(obligation_id,operator_id,result,executed_at,state,started_at,
                                       submitted_at,updated_at)
                VALUES (%d,%d,'COMPLETED','2031-01-01T15:00:00Z','SUBMITTED','2031-01-01T14:00:00Z',
                        '2031-01-01T15:00:00Z','2031-01-01T15:00:00Z') RETURNING id
                """.formatted(obligation, supervisor));
        long complianceCase = id(statement, """
                INSERT INTO compliance_cases(obligation_id,company_id,site_id,decision_version,created_by,
                                             created_at,updated_at)
                VALUES (%d,1,1,2,%d,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id
                """.formatted(obligation, supervisor));
        long firstResult = id(statement, resultSql(complianceCase, execution, 1, null, null, supervisor));
        long secondResult = id(statement, resultSql(complianceCase, execution, 2, firstResult,
                "Original reevaluation", supervisor));
        return new Fixture(complianceCase, firstResult, secondResult);
    }

    private String resultSql(long complianceCase, long execution, int version, Long predecessor,
                             String reason, long supervisor) {
        String predecessorValue = predecessor == null ? "NULL" : predecessor.toString();
        String reasonValue = reason == null ? "NULL" : "'" + reason + "'";
        return """
                INSERT INTO compliance_results(compliance_case_id,execution_id,decision_version,
                        predecessor_result_id,outcome,source_review_version,source_execution_version,
                        explanation,reevaluation_reason,decided_by,decided_at)
                VALUES (%d,%d,%d,%s,'COMPLIANT','execution-v0',0,'Decision',%s,%d,CURRENT_TIMESTAMP)
                RETURNING id
                """.formatted(complianceCase, execution, version, predecessorValue, reasonValue, supervisor);
    }

    private String observationSql(long complianceCase, long supervisor, UUID series, int revision,
                                  Long predecessor, String reason) {
        String predecessorValue = predecessor == null ? "NULL" : predecessor.toString();
        String reasonValue = reason == null ? "NULL" : "'" + reason + "'";
        return """
                INSERT INTO client_observations(compliance_case_id,series_id,revision,
                        supersedes_observation_id,description,origin,observed_date,amendment_reason,
                        recorded_by,recorded_at)
                VALUES (%d,'%s',%d,%s,'Client feedback','EMAIL','2031-01-01',%s,%d,CURRENT_TIMESTAMP)
                RETURNING id
                """.formatted(complianceCase, series, revision, predecessorValue, reasonValue, supervisor);
    }

    private long user(Statement statement) throws SQLException {
        return id(statement, """
                INSERT INTO users(full_name,email,password_hash,role,created_at,company_id,site_id)
                VALUES ('Supervisor','%s@example.test','not-a-password','SUPERVISOR',CURRENT_TIMESTAMP,1,1)
                RETURNING id
                """.formatted(UUID.randomUUID()));
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

    private record Fixture(long complianceCase, long result, long secondResult) {}
}
