package com.api.servicecompliance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

// Opt-in only against a dedicated local test database, never the shared backend database.
@EnabledIfEnvironmentVariable(named = "FIELD_OPERATIONS_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/field_operations_test")
class ObligationDetailsMigrationTests {
    @Test
    void migratesAnEmptySchemaAndEnforcesWindowOwnershipAndOrderingConstraints() throws Exception {
        String schema = newSchema();
        var migration = flyway(schema, "10").migrate();
        assertThat(migration.migrationsExecuted).isEqualTo(10);
        flyway(schema, "10").validate();

        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            long userId = insertOperator(statement);
            long obligationId = insertLegacyObligation(statement, userId);
            statement.executeUpdate("INSERT INTO obligation_steps (obligation_id, title, step_order) VALUES ("
                    + obligationId + ", 'Clean floor', 1)");
            statement.executeUpdate("INSERT INTO obligation_photo_requirements (obligation_id, title, photo_order) VALUES ("
                    + obligationId + ", 'Finished floor', 1)");

            assertSqlState(statement, "UPDATE obligations SET starts_at = due_at WHERE id = " + obligationId, "23514");
            assertSqlState(statement, "INSERT INTO obligation_steps (obligation_id, title, step_order) VALUES ("
                    + obligationId + ", 'Duplicate order', 1)", "23505");
            assertSqlState(statement, "INSERT INTO obligation_photo_requirements (obligation_id, title, photo_order) VALUES ("
                    + obligationId + ", 'Invalid order', 0)", "23514");
            assertSqlState(statement, "INSERT INTO obligation_steps (obligation_id, title, step_order) VALUES (-1, 'Orphan', 1)", "23503");
        }
    }

    @Test
    void upgradesV9WithoutInventingDetailsOrChangingExistingExecutionAndEvidence() throws Exception {
        String schema = newSchema();
        assertThat(flyway(schema, "9").migrate().migrationsExecuted).isEqualTo(9);
        long obligationId;
        long executionId;
        long evidenceId;
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            long userId = insertOperator(statement);
            obligationId = insertLegacyObligation(statement, userId);
            executionId = generatedId(statement, "INSERT INTO executions (obligation_id, operator_id, result, notes, executed_at) "
                    + "VALUES (" + obligationId + ", " + userId + ", 'COMPLETED', 'Preserve this result', CURRENT_TIMESTAMP) RETURNING id");
            evidenceId = generatedId(statement, "INSERT INTO evidences (execution_id, type, url, description, created_at) "
                    + "VALUES (" + executionId + ", 'PHOTO', 'https://example.test/existing.jpg', 'Existing reference', CURRENT_TIMESTAMP) RETURNING id");
        }

        assertThat(flyway(schema, "10").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema, "10").validate();
        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            try (ResultSet row = statement.executeQuery("SELECT title, starts_at, status FROM obligations WHERE id = " + obligationId)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("title")).isEqualTo("Existing obligation");
                assertThat(row.getObject("starts_at")).isNull();
                assertThat(row.getString("status")).isEqualTo("ASSIGNED");
            }
            try (ResultSet row = statement.executeQuery("SELECT result, notes FROM executions WHERE id = " + executionId)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("result")).isEqualTo("COMPLETED");
                assertThat(row.getString("notes")).isEqualTo("Preserve this result");
            }
            try (ResultSet row = statement.executeQuery("SELECT execution_id, url FROM evidences WHERE id = " + evidenceId)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("execution_id")).isEqualTo(executionId);
                assertThat(row.getString("url")).isEqualTo("https://example.test/existing.jpg");
            }
            for (String table : new String[]{"obligation_steps", "obligation_photo_requirements"}) {
                try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertThat(count.next()).isTrue();
                    assertThat(count.getLong(1)).isZero();
                }
            }
        }
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(url(), username(), password()).schemas(schema)
                .locations("classpath:db/migration").target(target).load();
    }

    private Connection connect(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(url(), username(), password());
        connection.setSchema(schema);
        return connection;
    }

    private long insertOperator(Statement statement) throws SQLException {
        return generatedId(statement, "INSERT INTO users (full_name, email, password_hash, role, created_at, company_id, site_id) "
                + "VALUES ('Migration fixture', 'migration@example.test', 'not-a-login-password', 'OPERATOR', CURRENT_TIMESTAMP, 1, 1) RETURNING id");
    }

    private long insertLegacyObligation(Statement statement, long userId) throws SQLException {
        return generatedId(statement, "INSERT INTO obligations (title, description, site_name, assigned_operator_id, status, due_at, company_id, site_id) "
                + "VALUES ('Existing obligation', 'Preserve instructions', 'Legacy site', " + userId
                + ", 'ASSIGNED', '2030-01-01T15:30:00Z', 1, 1) RETURNING id");
    }

    private long generatedId(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private void assertSqlState(Statement statement, String sql, String expectedState) {
        assertThatThrownBy(() -> statement.executeUpdate(sql)).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(expectedState));
    }

    private String newSchema() { return "field_ops_" + UUID.randomUUID().toString().replace("-", ""); }
    private String url() { return System.getenv("FIELD_OPERATIONS_TEST_JDBC_URL"); }
    private String username() { return System.getenv("FIELD_OPERATIONS_TEST_DB_USER"); }
    private String password() { return System.getenv().getOrDefault("FIELD_OPERATIONS_TEST_DB_PASSWORD", ""); }
}
