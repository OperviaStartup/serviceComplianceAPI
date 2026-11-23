package com.api.servicecompliance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FIELD_OPERATIONS_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/field_operations_test")
class OperationalStatusMigrationTests {
    @Test
    void addsOperationalProjectionIndexesWithoutChangingEarlierMigrations() throws Exception {
        String schema = "operational_status_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(flyway(schema, "20").migrate().migrationsExecuted).isEqualTo(20);
        assertThat(flyway(schema, "21").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema, "21").validate();

        try (var connection = DriverManager.getConnection(url(), user(), password());
             var statement = connection.prepareStatement("""
                     SELECT indexname FROM pg_indexes
                     WHERE schemaname = ? AND indexname IN (
                       'idx_obligations_operational_reporting',
                       'idx_obligations_source_plan',
                       'idx_operational_exceptions_execution')
                     """)) {
            statement.setString(1, schema);
            try (var result = statement.executeQuery()) {
                var names = new java.util.HashSet<String>();
                while (result.next()) names.add(result.getString(1));
                assertThat(names).containsExactlyInAnyOrder(
                        "idx_obligations_operational_reporting",
                        "idx_obligations_source_plan",
                        "idx_operational_exceptions_execution");
            }
        }
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(url(), user(), password()).schemas(schema)
                .locations("classpath:db/migration").target(target).load();
    }

    private String url() { return System.getenv("FIELD_OPERATIONS_TEST_JDBC_URL"); }
    private String user() { return System.getenv("FIELD_OPERATIONS_TEST_DB_USER"); }
    private String password() { return System.getenv().getOrDefault("FIELD_OPERATIONS_TEST_DB_PASSWORD", ""); }
}
