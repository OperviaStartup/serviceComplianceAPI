package com.api.servicecompliance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="FIELD_OPERATIONS_TEST_JDBC_URL",
        matches="jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/field_operations_test")
class OperatorFlowMigrationTests {
    @Test void upgradePreservesHistoricalResultsAndDoesNotInventLifecycleTimestamps() throws Exception {
        String schema=schema();
        assertThat(flyway(schema,"10").migrate().migrationsExecuted).isEqualTo(10);
        long execution;
        try(var connection=connection(schema); var statement=connection.createStatement()) {
            long operator=operator(statement), obligation=obligation(statement,operator);
            execution=id(statement,"INSERT INTO executions(obligation_id,operator_id,result,notes,executed_at) VALUES ("
                    +obligation+","+operator+",'PARTIAL','Preserve facts','2029-01-01T15:00:00Z') RETURNING id");
            statement.executeUpdate("INSERT INTO evidences(execution_id,type,url,created_at) VALUES ("+execution
                    +",'PHOTO','https://example.test/legacy.jpg','2029-01-01T15:00:00Z')");
        }
        assertThat(flyway(schema,"latest").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema,"latest").validate();
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            try(var result=statement.executeQuery("SELECT state,result,notes,started_at,submitted_at,executed_at FROM executions WHERE id="+execution)) {
                assertThat(result.next()).isTrue(); assertThat(result.getString("state")).isEqualTo("LEGACY");
                assertThat(result.getString("result")).isEqualTo("PARTIAL");
                assertThat(result.getString("notes")).isEqualTo("Preserve facts");
                assertThat(result.getObject("started_at")).isNull(); assertThat(result.getObject("submitted_at")).isNull();
                assertThat(result.getTimestamp("executed_at").toInstant().toString()).isEqualTo("2029-01-01T15:00:00Z");
            }
            try(var result=statement.executeQuery("SELECT url,storage_key,photo_requirement_id FROM evidences WHERE execution_id="+execution)) {
                assertThat(result.next()).isTrue(); assertThat(result.getString("url")).isEqualTo("https://example.test/legacy.jpg");
                assertThat(result.getString("storage_key")).isNull(); assertThat(result.getObject("photo_requirement_id")).isNull();
            }
        }
    }

    @Test void freshSchemaEnforcesUniqueExecutionAndPhotoAndRejectsNullLifecycleFacts() throws Exception {
        String schema=schema(); assertThat(flyway(schema,"latest").migrate().migrationsExecuted).isEqualTo(11);
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            long operator=operator(statement), obligation=obligation(statement,operator);
            String start="INSERT INTO executions(obligation_id,operator_id,state,started_at,executed_at) VALUES ("
                    +obligation+","+operator+",'IN_PROGRESS','2030-01-01T15:00:00Z','2030-01-01T15:00:00Z')";
            long execution=id(statement,start+" RETURNING id");
            rejects(statement,start,"23505");
            rejects(statement,"UPDATE executions SET state='SUBMITTED',submitted_at=NULL WHERE id="+execution,"23514");
            rejects(statement,"UPDATE executions SET state='SUBMITTED',submitted_at='2030-01-01T15:30:00Z',result=NULL WHERE id="+execution,"23514");
            long requirement=id(statement,"INSERT INTO obligation_photo_requirements(obligation_id,title,photo_order) VALUES ("
                    +obligation+",'Floor',1) RETURNING id");
            String photo="INSERT INTO evidences(execution_id,type,photo_requirement_id,storage_key,content_type,file_size,checksum,created_at) VALUES ("
                    +execution+",'PHOTO',"+requirement+",'company/1/photo.png','image/png',3,'"+"a".repeat(64)+"',CURRENT_TIMESTAMP)";
            statement.executeUpdate(photo); rejects(statement,photo,"23505");
            rejects(statement,"UPDATE evidences SET file_size=NULL WHERE execution_id="+execution,"23514");
            rejects(statement,"UPDATE evidences SET content_type=NULL WHERE execution_id="+execution,"23514");
        }
    }

    private void rejects(Statement statement,String sql,String state) {
        assertThatThrownBy(() -> statement.executeUpdate(sql)).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException)error).getSQLState()).isEqualTo(state));
    }
    private long operator(Statement statement) throws SQLException {
        return id(statement,"INSERT INTO users(full_name,email,password_hash,role,created_at,company_id,site_id) "
                +"VALUES ('Migration fixture','migration@example.test','not-a-login-password','OPERATOR',CURRENT_TIMESTAMP,1,1) RETURNING id");
    }
    private long obligation(Statement statement,long operator) throws SQLException {
        return id(statement,"INSERT INTO obligations(title,description,site_name,assigned_operator_id,status,due_at,company_id,site_id) "
                +"VALUES ('Fixture','Fixture','Site',"+operator+",'ASSIGNED','2030-01-01T15:30:00Z',1,1) RETURNING id");
    }
    private long id(Statement statement,String sql) throws SQLException {
        try(var result=statement.executeQuery(sql)) { assertThat(result.next()).isTrue(); return result.getLong(1); }
    }
    private String schema() { return "operator_flow_"+UUID.randomUUID().toString().replace("-",""); }
    private Flyway flyway(String schema,String target) {
        return Flyway.configure().dataSource(System.getenv("FIELD_OPERATIONS_TEST_JDBC_URL"),
                System.getenv("FIELD_OPERATIONS_TEST_DB_USER"),System.getenv().getOrDefault("FIELD_OPERATIONS_TEST_DB_PASSWORD",""))
                .schemas(schema).locations("classpath:db/migration").target(target).load();
    }
    private Connection connection(String schema) throws SQLException {
        var connection=DriverManager.getConnection(System.getenv("FIELD_OPERATIONS_TEST_JDBC_URL"),
                System.getenv("FIELD_OPERATIONS_TEST_DB_USER"),System.getenv().getOrDefault("FIELD_OPERATIONS_TEST_DB_PASSWORD",""));
        connection.setSchema(schema); return connection;
    }
}
