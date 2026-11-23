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
class ComplianceDecisionMigrationTests {
    @Test
    void addsAppendOnlyCasesResultsCriteriaAndFindings() throws Exception {
        String schema = "compliance_decision_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(flyway(schema, "21").migrate().migrationsExecuted).isEqualTo(21);
        assertThat(flyway(schema, "22").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema, "22").validate();

        try (Connection connection = connection(schema); Statement statement = connection.createStatement()) {
            long supervisor = id(statement, """
                    INSERT INTO users(full_name,email,password_hash,role,created_at,company_id,site_id)
                    VALUES ('Supervisor','decision-migration@example.test','not-a-password','SUPERVISOR',
                            CURRENT_TIMESTAMP,1,1) RETURNING id
                    """);
            long obligation = id(statement, """
                    INSERT INTO obligations(title,description,site_name,assigned_operator_id,status,due_at,
                                            company_id,site_id)
                    VALUES ('Fixture','Fixture','Site',%d,'SUBMITTED','2031-01-01T15:30:00Z',1,1)
                    RETURNING id
                    """.formatted(supervisor));
            long execution = id(statement, """
                    INSERT INTO executions(obligation_id,operator_id,result,executed_at,state,started_at,
                                           submitted_at,updated_at)
                    VALUES (%d,%d,'COMPLETED','2031-01-01T15:00:00Z','SUBMITTED',
                            '2031-01-01T14:00:00Z','2031-01-01T15:00:00Z','2031-01-01T15:00:00Z')
                    RETURNING id
                    """.formatted(obligation, supervisor));
            long complianceCase = id(statement, """
                    INSERT INTO compliance_cases(obligation_id,company_id,site_id,decision_version,created_by,
                                                 created_at,updated_at)
                    VALUES (%d,1,1,1,%d,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id
                    """.formatted(obligation, supervisor));
            long result = id(statement, """
                    INSERT INTO compliance_results(compliance_case_id,execution_id,decision_version,outcome,
                                                   source_review_version,source_execution_version,explanation,
                                                   decided_by,decided_at)
                    VALUES (%d,%d,1,'COMPLIANT','execution-%d-v0',0,'Compliant',%d,CURRENT_TIMESTAMP)
                    RETURNING id
                    """.formatted(complianceCase, execution, execution, supervisor));
            statement.executeUpdate("""
                    INSERT INTO compliance_result_criteria(compliance_result_id,position,code,status)
                    VALUES (%d,1,'SERVICE_QUALITY','PASS')
                    """.formatted(result));

            rejects(statement, """
                    INSERT INTO compliance_cases(obligation_id,company_id,site_id,created_by,created_at,updated_at)
                    VALUES (%d,1,1,%d,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """.formatted(obligation, supervisor), "23505");
            rejects(statement, """
                    INSERT INTO compliance_results(compliance_case_id,execution_id,decision_version,outcome,
                                                   source_review_version,source_execution_version,explanation,
                                                   decided_by,decided_at)
                    VALUES (%d,%d,1,'COMPLIANT','execution-%d-v0',0,'Duplicate',%d,CURRENT_TIMESTAMP)
                    """.formatted(complianceCase, execution, execution, supervisor), "23505");
            rejects(statement, """
                    INSERT INTO compliance_results(compliance_case_id,execution_id,decision_version,
                                                   predecessor_result_id,outcome,source_review_version,
                                                   source_execution_version,explanation,decided_by,decided_at)
                    VALUES (%d,%d,2,%d,'NON_COMPLIANT','execution-%d-v0',0,'Changed',%d,CURRENT_TIMESTAMP)
                    """.formatted(complianceCase, execution, result, execution, supervisor), "23514");
            rejects(statement, """
                    INSERT INTO compliance_result_findings(compliance_result_id,position,code,description,
                                                           exception_disposition)
                    VALUES (%d,1,'EXCEPTION_REVIEW','Invalid partial reference','ACCEPTED')
                    """.formatted(result), "23514");
        }
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
}
