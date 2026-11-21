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
        assertThat(flyway(schema,"11").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema,"11").validate();
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
        String schema=schema(); assertThat(flyway(schema,"11").migrate().migrationsExecuted).isEqualTo(11);
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

    @Test void activationMigrationUpgradesFieldOperationsSchemaWithoutChangingEarlierMigrations() throws Exception {
        String schema=schema();
        assertThat(flyway(schema,"11").migrate().migrationsExecuted).isEqualTo(11);
        long plan;
        try (var connection=connection(schema); var statement=connection.createStatement()) {
            long actor=operator(statement);
            plan=id(statement,"INSERT INTO service_plans(company_id,service_id,site_id,start_date,end_date,timezone,state,created_by,created_at,updated_at) "
                    +"VALUES (1,1,1,'2030-01-01','2030-01-31','America/Lima','DRAFT',"+actor+",CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id");
        }
        assertThat(flyway(schema,"latest").migrate().migrationsExecuted).isEqualTo(9);
        flyway(schema,"latest").validate();
        try (var connection=connection(schema); var statement=connection.createStatement()) {
            try (var result=statement.executeQuery("SELECT version, success FROM flyway_schema_history WHERE version IN ('10','11','12') ORDER BY installed_rank")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString("version")).isEqualTo("10"); assertThat(result.getBoolean("success")).isTrue();
                assertThat(result.next()).isTrue(); assertThat(result.getString("version")).isEqualTo("11"); assertThat(result.getBoolean("success")).isTrue();
                assertThat(result.next()).isTrue(); assertThat(result.getString("version")).isEqualTo("12"); assertThat(result.getBoolean("success")).isTrue();
                assertThat(result.next()).isFalse();
            }
            try (var result=statement.executeQuery("SELECT state, business_version, series_id FROM service_plans WHERE id="+plan)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("state")).isEqualTo("DRAFT");
                assertThat(result.getInt("business_version")).isEqualTo(1);
                assertThat(result.getObject("series_id")).isNotNull();
            }
        }
    }

    @Test void executionIdempotencyMigrationEnforcesScopeAndCascadesWithExecution() throws Exception {
        String schema=schema(); assertThat(flyway(schema,"15").migrate().migrationsExecuted).isEqualTo(15);
        long execution;
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            long operator=operator(statement), obligation=obligation(statement,operator);
            execution=id(statement,"INSERT INTO executions(obligation_id,operator_id,state,started_at,executed_at,updated_at) VALUES ("
                    +obligation+","+operator+",'IN_PROGRESS','2030-01-01T15:00:00Z','2030-01-01T15:00:00Z','2030-01-01T15:00:00Z') RETURNING id");
        }
        assertThat(flyway(schema,"16").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema,"16").validate();
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            long operator;
            try(var result=statement.executeQuery("SELECT operator_id FROM executions WHERE id="+execution)) {
                assertThat(result.next()).isTrue(); operator=result.getLong(1);
            }
            String record="INSERT INTO execution_idempotency(company_id,actor_id,command,idempotency_key,request_hash,execution_id,created_at,expires_at) VALUES "
                    +"(1,"+operator+",'START_EXECUTION','start-migration-key','"+"a".repeat(64)+"',"+execution
                    +",CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '7 days')";
            statement.executeUpdate(record); rejects(statement,record,"23505");
            statement.executeUpdate("DELETE FROM executions WHERE id="+execution);
            try(var result=statement.executeQuery("SELECT count(*) FROM execution_idempotency WHERE execution_id="+execution)) {
                assertThat(result.next()).isTrue(); assertThat(result.getLong(1)).isZero();
            }
        }
    }

    @Test void requiredEvidenceMigrationEnforcesSnapshotsCountsAndIdempotency() throws Exception {
        String schema=schema(); assertThat(flyway(schema,"17").migrate().migrationsExecuted).isEqualTo(17);
        flyway(schema,"17").validate();
        assertThat(flyway(schema,"18").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema,"18").validate();
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            long operator=operator(statement), obligation=obligation(statement,operator);
            long execution=id(statement,"INSERT INTO executions(obligation_id,operator_id,state,started_at,executed_at,updated_at) VALUES ("
                    +obligation+","+operator+",'IN_PROGRESS','2030-01-01T15:00:00Z','2030-01-01T15:00:00Z','2030-01-01T15:00:00Z') RETURNING id");
            long requirement=id(statement,"INSERT INTO obligation_evidence_requirements(obligation_id,source_requirement_id,evidence_type,is_required,minimum_count,maximum_count,requirement_order) VALUES ("
                    +obligation+",987654321,'QR_CODE',TRUE,1,1,1) RETURNING id");
            rejects(statement,"INSERT INTO obligation_evidence_requirements(obligation_id,source_requirement_id,evidence_type,is_required,minimum_count,maximum_count,requirement_order) VALUES ("
                    +obligation+",987654322,'PHOTO',TRUE,2,1,2)","23514");
            long stored=id(statement,"INSERT INTO evidences(execution_id,type,evidence_requirement_id,created_by,text_value,file_size,checksum,captured_at,created_at) VALUES ("
                    +execution+",'QR_CODE',"+requirement+","+operator+",'OPER-123',8,'"+"a".repeat(64)+"','2030-01-01T15:01:00Z','2030-01-01T15:01:01Z') RETURNING id");
            rejects(statement,"INSERT INTO evidences(execution_id,type,evidence_requirement_id,created_by,text_value,captured_at,created_at) VALUES ("
                    +execution+",'LOCATION',"+requirement+","+operator+",'not-location','2030-01-01T15:01:00Z','2030-01-01T15:01:01Z')","23514");
            String record="INSERT INTO evidence_idempotency(company_id,actor_id,command,idempotency_key,request_hash,evidence_id,created_at,expires_at) VALUES "
                    +"(1,"+operator+",'ATTACH_EVIDENCE','evidence-migration-key','"+"a".repeat(64)+"',"+stored
                    +",CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '7 days')";
            statement.executeUpdate(record); rejects(statement,record,"23505");
            statement.executeUpdate("DELETE FROM evidences WHERE id="+stored);
            try(var result=statement.executeQuery("SELECT count(*) FROM evidence_idempotency WHERE evidence_id="+stored)) {
                assertThat(result.next()).isTrue(); assertThat(result.getLong(1)).isZero();
            }
        }
    }

    @Test void exceptionEvidenceMigrationEnforcesTheSameExecutionAndRetainsReferencedEvidence() throws Exception {
        String schema=schema();
        assertThat(flyway(schema,"18").migrate().migrationsExecuted).isEqualTo(18);
        long exceptionId;
        long execution;
        long evidence;
        long foreignEvidence;
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            long operator=operator(statement);
            long obligation=obligation(statement,operator);
            execution=id(statement,"INSERT INTO executions(obligation_id,operator_id,state,started_at,executed_at,updated_at) VALUES ("
                    +obligation+","+operator+",'IN_PROGRESS','2030-01-01T15:00:00Z','2030-01-01T15:00:00Z','2030-01-01T15:00:00Z') RETURNING id");
            evidence=id(statement,"INSERT INTO evidences(execution_id,type,url,created_at) VALUES ("
                    +execution+",'PHOTO','https://example.test/evidence.png',CURRENT_TIMESTAMP) RETURNING id");
            exceptionId=id(statement,"INSERT INTO operational_exceptions(execution_id,reason,description,reported_by,reported_at,operation_id) VALUES ("
                    +execution+",'AREA_CLOSED','Door is locked',"+operator+",CURRENT_TIMESTAMP,'"
                    +UUID.randomUUID()+"') RETURNING id");
            long otherObligation=obligation(statement,operator);
            long otherExecution=id(statement,"INSERT INTO executions(obligation_id,operator_id,state,started_at,executed_at,updated_at) VALUES ("
                    +otherObligation+","+operator+",'IN_PROGRESS','2030-01-01T15:00:00Z','2030-01-01T15:00:00Z','2030-01-01T15:00:00Z') RETURNING id");
            foreignEvidence=id(statement,"INSERT INTO evidences(execution_id,type,url,created_at) VALUES ("
                    +otherExecution+",'PHOTO','https://example.test/foreign.png',CURRENT_TIMESTAMP) RETURNING id");
        }
        assertThat(flyway(schema,"19").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema,"19").validate();
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            String reference="INSERT INTO operational_exception_evidence(exception_id,evidence_id,execution_id) VALUES ("
                    +exceptionId+","+evidence+","+execution+")";
            statement.executeUpdate(reference);
            rejects(statement,reference,"23505");
            rejects(statement,"INSERT INTO operational_exception_evidence(exception_id,evidence_id,execution_id) VALUES ("
                    +exceptionId+","+foreignEvidence+","+execution+")","23503");
            rejects(statement,"DELETE FROM evidences WHERE id="+evidence,"23503");
            statement.executeUpdate("DELETE FROM operational_exceptions WHERE id="+exceptionId);
            try(var result=statement.executeQuery("SELECT count(*) FROM operational_exception_evidence WHERE exception_id="+exceptionId)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isZero();
            }
        }
    }

    @Test void offlineSyncMigrationScopesOperationsAndValidatesTerminalResults() throws Exception {
        String schema=schema();
        assertThat(flyway(schema,"19").migrate().migrationsExecuted).isEqualTo(19);
        assertThat(flyway(schema,"20").migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema,"20").validate();
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            long operator=operator(statement); UUID operation=UUID.randomUUID();
            String synced="INSERT INTO offline_sync_operations(company_id,actor_id,client_operation_id,operation_type,request_hash,outcome,result_code,resource_type,resource_id,current_version,created_at,expires_at) VALUES (1,"
                    +operator+",'"+operation+"','START_EXECUTION','"+"a".repeat(64)
                    +"','SYNCED','SYNCED','EXECUTION',123,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '30 days')";
            statement.executeUpdate(synced); rejects(statement,synced,"23505");
            rejects(statement,"INSERT INTO offline_sync_operations(company_id,actor_id,client_operation_id,operation_type,request_hash,outcome,result_code,created_at,expires_at) VALUES (1,"
                    +operator+",'"+UUID.randomUUID()+"','START_EXECUTION','"+"b".repeat(64)
                    +"','RETRYABLE','TEMPORARY_FAILURE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '30 days')","23514");
            rejects(statement,"INSERT INTO offline_sync_operations(company_id,actor_id,client_operation_id,operation_type,request_hash,outcome,result_code,resource_type,created_at,expires_at) VALUES (1,"
                    +operator+",'"+UUID.randomUUID()+"','START_EXECUTION','"+"c".repeat(64)
                    +"','REQUIRES_ATTENTION','VALIDATION_ERROR','EXECUTION',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '30 days')","23514");
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
