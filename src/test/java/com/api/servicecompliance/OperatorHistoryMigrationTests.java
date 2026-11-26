package com.api.servicecompliance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FIELD_OPERATIONS_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/field_operations_test")
class OperatorHistoryMigrationTests {
    @Test void upgradesExistingHistoryWithoutChangingItsRecords() throws Exception {
        String schema = schema();
        assertThat(flyway(schema, "22").migrate().migrationsExecuted).isEqualTo(22);
        long execution;
        try (Connection connection = connection(schema); var statement = connection.createStatement()) {
            long operator = id(statement, """
                    INSERT INTO users(full_name,email,password_hash,role,created_at,company_id,site_id)
                    VALUES ('History fixture','history-migration@example.test','test-only','OPERATOR',CURRENT_TIMESTAMP,1,1)
                    RETURNING id
                    """);
            long obligation = id(statement, """
                    INSERT INTO obligations(title,description,site_name,assigned_operator_id,status,due_at,company_id,site_id)
                    VALUES ('History fixture','Fixture','Site',%d,'SUBMITTED','2030-01-03T20:00:00Z',1,1) RETURNING id
                    """.formatted(operator));
            execution = id(statement, """
                    INSERT INTO executions(obligation_id,operator_id,result,executed_at,state,started_at,submitted_at,notes)
                    VALUES (%d,%d,'COMPLETED','2030-01-03T15:00:00Z','SUBMITTED','2030-01-03T15:00:00Z','2030-01-03T16:00:00Z','Preserve me')
                    RETURNING id
                    """.formatted(obligation, operator));
        }
        assertThat(flyway(schema, "24").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema, "24").validate();
        try (Connection connection = connection(schema); var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("SELECT state,result,notes FROM executions WHERE id=" + execution)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("state")).isEqualTo("SUBMITTED");
                assertThat(row.getString("result")).isEqualTo("COMPLETED");
                assertThat(row.getString("notes")).isEqualTo("Preserve me");
                assertThat(row.next()).isFalse();
            }
            try (var row = statement.executeQuery("SELECT count(*) FROM pg_indexes WHERE schemaname='" + schema
                    + "' AND indexname IN ('idx_execution_history_owner_recorded','idx_compliance_results_execution_version')")) {
                assertThat(row.next()).isTrue(); assertThat(row.getInt(1)).isEqualTo(2);
            }
        }
        assertThat(flyway(schema, "24").migrate().migrationsExecuted).isZero();
    }

    @Test void installsAllMigrationsOnAnEmptySchema() {
        String schema = schema();
        assertThat(flyway(schema, "24").migrate().migrationsExecuted).isEqualTo(23);
        flyway(schema, "24").validate();
    }

    private String schema() { return "operator_history_" + UUID.randomUUID().toString().replace("-", ""); }
    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(url(), user(), password()).schemas(schema)
                .locations("classpath:db/migration").target(target).load();
    }
    private Connection connection(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(url(), user(), password());
        connection.setSchema(schema); return connection;
    }
    private long id(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) { assertThat(result.next()).isTrue(); return result.getLong(1); }
    }
    private String url() { return System.getenv("FIELD_OPERATIONS_TEST_JDBC_URL"); }
    private String user() { return System.getenv("FIELD_OPERATIONS_TEST_DB_USER"); }
    private String password() { return System.getenv().getOrDefault("FIELD_OPERATIONS_TEST_DB_PASSWORD", ""); }
}
