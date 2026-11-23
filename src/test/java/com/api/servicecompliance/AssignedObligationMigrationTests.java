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

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FIELD_OPERATIONS_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/field_operations_test")
class AssignedObligationMigrationTests {
    @Test
    void migrationRecordsAssignmentReassignmentAndStatusChangesAtomically() throws Exception {
        String schema = "assigned_sync_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure().dataSource(url(), username(), password()).schemas(schema)
                .locations("classpath:db/migration").target("15").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(15);
        flyway.validate();

        try (Connection connection = connect(schema); Statement statement = connection.createStatement()) {
            long firstOperator = operator(statement, "first");
            long secondOperator = operator(statement, "second");
            long obligation = id(statement, "INSERT INTO obligations "
                    + "(title, description, site_name, assigned_operator_id, status, due_at, company_id, site_id) "
                    + "VALUES ('Lobby', 'Clean lobby', 'Site 1', " + firstOperator
                    + ", 'ASSIGNED', '2030-01-01T15:30:00Z', 1, 1) RETURNING id");
            statement.executeUpdate("UPDATE obligations SET assigned_operator_id = " + secondOperator
                    + " WHERE id = " + obligation);
            statement.executeUpdate("UPDATE obligations SET status = 'IN_PROGRESS' WHERE id = " + obligation);
            statement.executeUpdate("UPDATE obligations SET description = 'No sync event' WHERE id = " + obligation);

            try (ResultSet result = statement.executeQuery("SELECT operator_id, change_type "
                    + "FROM obligation_assignment_changes ORDER BY id")) {
                assertChange(result, firstOperator, "UPSERT");
                assertChange(result, firstOperator, "REMOVED");
                assertChange(result, secondOperator, "UPSERT");
                assertChange(result, secondOperator, "UPSERT");
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertChange(ResultSet result, long operatorId, String type) throws SQLException {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong("operator_id")).isEqualTo(operatorId);
        assertThat(result.getString("change_type")).isEqualTo(type);
    }

    private long operator(Statement statement, String prefix) throws SQLException {
        return id(statement, "INSERT INTO users (full_name, email, password_hash, role, created_at, company_id, site_id) "
                + "VALUES ('Migration operator', '" + prefix + "@migration.test', 'hash', 'OPERATOR', "
                + "CURRENT_TIMESTAMP, 1, 1) RETURNING id");
    }

    private long id(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private Connection connect(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(url(), username(), password());
        connection.setSchema(schema);
        return connection;
    }

    private String url() { return System.getenv("FIELD_OPERATIONS_TEST_JDBC_URL"); }
    private String username() { return System.getenv("FIELD_OPERATIONS_TEST_DB_USER"); }
    private String password() { return System.getenv().getOrDefault("FIELD_OPERATIONS_TEST_DB_PASSWORD", ""); }
}
