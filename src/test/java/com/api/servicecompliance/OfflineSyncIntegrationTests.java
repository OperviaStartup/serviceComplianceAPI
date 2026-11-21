package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.infrastructure.security.JwtService;
import com.api.servicecompliance.executions.application.*;
import com.api.servicecompliance.executions.domain.model.EvidenceType;
import com.api.servicecompliance.executions.domain.repository.*;
import com.api.servicecompliance.obligations.domain.model.*;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.jpa.open-in-view=false","app.field-operations.expiry-enabled=false"})
@AutoConfigureMockMvc
@Import(OfflineSyncIntegrationTests.Fixtures.class)
class OfflineSyncIntegrationTests {
    private static final Instant START=Instant.parse("2030-01-01T15:00:00Z");
    private static final AtomicLong SOURCE_REQUIREMENT_IDS=new AtomicLong(900);
    @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt; @Autowired ObligationRepository obligations;
    @Autowired ExecutionRepository executions; @Autowired EvidenceRepository evidence;
    @Autowired ExecutionStepProgressRepository progress; @Autowired OperationalExceptionRepository exceptions;
    @Autowired OperationalExceptionEvidenceRepository exceptionEvidence;
    @Autowired OfflineSyncOperationRepository syncOperations; @Autowired MutableClock clock;
    private final ObjectMapper json=new ObjectMapper();
    private User owner,supervisor; private String ownerToken,supervisorToken;
    private final List<User> fixtures=new ArrayList<>();

    @BeforeEach void setUp() {
        clock.set(START.plusSeconds(60));
        owner=user(Role.OPERATOR); supervisor=user(Role.SUPERVISOR);
        ownerToken="Bearer "+jwt.createToken(owner); supervisorToken="Bearer "+jwt.createToken(supervisor);
    }

    @AfterEach void clean() {
        syncOperations.deleteAll();
        for(User user:fixtures) for(Obligation obligation:obligations.findByAssignedOperatorId(user.getId())) {
            for(var execution:executions.findByObligationId(obligation.getId())) {
                var ids=exceptions.findByExecutionIdOrderByReportedAtAscIdAsc(execution.getId()).stream()
                        .map(com.api.servicecompliance.executions.domain.model.OperationalException::getId).toList();
                exceptionEvidence.deleteAll(exceptionEvidence.findByExceptionIdIn(ids));
                exceptions.deleteAll(exceptions.findByExecutionIdOrderByReportedAtAscIdAsc(execution.getId()));
                progress.deleteAll(progress.findByExecutionId(execution.getId()));
                evidence.deleteAll(evidence.findByExecutionId(execution.getId()));
                executions.delete(execution);
            }
            obligations.delete(obligation);
        }
        users.deleteAll(fixtures); fixtures.clear();
    }

    @Test void reordersDependenciesAndReplaysTheCompleteOfflineJourney() throws Exception {
        Obligation obligation=obligation(EvidenceType.QR_CODE);
        long stepId=obligation.getSteps().get(0).getId();
        long requirementId=obligation.getEvidenceRequirements().get(0).getId();
        UUID start=UUID.randomUUID(),attach=UUID.randomUUID(),exception=UUID.randomUUID();
        UUID step=UUID.randomUUID(),submit=UUID.randomUUID();
        ObjectNode manifest=batch();
        manifest.withArray("items")
                .add(reference(item(submit,"SUBMIT_EXECUTION").put("executionRef",start.toString())
                        .put("expectedVersion",3),"dependsOn",step))
                .add(reference(reference(item(exception,"REPORT_EXCEPTION")
                        .put("executionRef",start.toString()).put("reason","AREA_CLOSED")
                        .put("description","Door locked").put("expectedVersion",1),
                        "evidenceRefs",attach),"dependsOn",attach))
                .add(reference(item(step,"UPDATE_STEP").put("executionRef",start.toString())
                        .put("stepId",stepId).put("completed",true).put("expectedVersion",2),
                        "dependsOn",exception))
                .add(reference(item(attach,"ATTACH_EVIDENCE").put("executionRef",start.toString())
                        .put("evidenceRequirementId",requirementId).put("textValue","OPER-001")
                        .put("capturedAt",clock.instant().toString()).put("expectedVersion",0),
                        "dependsOn",start))
                .add(item(start,"START_EXECUTION").put("obligationId",obligation.getId()));

        JsonNode first=sync(manifest,ownerToken);
        assertThat(first.path("items")).hasSize(5);
        assertThat(first.path("items")).allMatch(result -> result.path("outcome").asText().equals("SYNCED"));
        assertThat(first.path("items").get(0).path("clientOperationId").asText()).isEqualTo(submit.toString());
        assertThat(executions.findByObligationId(obligation.getId()).get(0).getState().name()).isEqualTo("SUBMITTED");
        assertThat(evidence.findAll()).hasSize(1);
        assertThat(exceptions.findAll()).hasSize(1);

        JsonNode replay=sync(manifest,ownerToken);
        assertThat(replay).isEqualTo(first);
        assertThat(executions.findByObligationId(obligation.getId())).hasSize(1);
        assertThat(evidence.findAll()).hasSize(1);
        assertThat(exceptions.findAll()).hasSize(1);

        ((ObjectNode)manifest.withArray("items").get(0)).put("notes","changed");
        JsonNode mismatch=sync(manifest,ownerToken);
        assertThat(mismatch.path("items").get(0).path("outcome").asText()).isEqualTo("REQUIRES_ATTENTION");
        assertThat(mismatch.path("items").get(0).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_MISMATCH");
    }

    @Test void preservesSuccessfulItemsWhenAnotherItemNeedsAttention() throws Exception {
        Obligation obligation=obligation(null); UUID start=UUID.randomUUID(),invalid=UUID.randomUUID();
        ObjectNode manifest=batch(); UUID missing=UUID.randomUUID();
        manifest.withArray("items")
                .add(item(invalid,"UPDATE_STEP").put("executionRef",missing.toString()).put("stepId",1)
                        .put("completed",true).put("expectedVersion",0))
                .add(item(start,"START_EXECUTION").put("obligationId",obligation.getId()));
        JsonNode response=sync(manifest,ownerToken);
        assertThat(response.path("items").get(0).path("code").asText()).isEqualTo("DEPENDENCY_NOT_FOUND");
        assertThat(response.path("items").get(1).path("outcome").asText()).isEqualTo("SYNCED");
        assertThat(executions.findByObligationId(obligation.getId())).hasSize(1);
    }

    @Test void returnsCurrentVersionForAnOptimisticConflict() throws Exception {
        Obligation obligation=obligation(null); UUID start=UUID.randomUUID(),step=UUID.randomUUID();
        ObjectNode manifest=batch();
        manifest.withArray("items")
                .add(reference(item(step,"UPDATE_STEP").put("executionRef",start.toString())
                        .put("stepId",obligation.getSteps().get(0).getId()).put("completed",true)
                        .put("expectedVersion",9),"dependsOn",start))
                .add(item(start,"START_EXECUTION").put("obligationId",obligation.getId()));
        JsonNode response=sync(manifest,ownerToken);
        assertThat(response.path("items").get(0).path("code").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(response.path("items").get(0).path("currentVersion").asLong()).isZero();
    }

    @Test void supportsBinaryEvidenceAndRejectsNonOperatorsAtTheBoundary() throws Exception {
        Obligation obligation=obligation(EvidenceType.PHOTO); UUID start=UUID.randomUUID(),attach=UUID.randomUUID();
        ObjectNode manifest=batch();
        manifest.withArray("items")
                .add(reference(item(attach,"ATTACH_EVIDENCE").put("executionRef",start.toString())
                        .put("evidenceRequirementId",obligation.getEvidenceRequirements().get(0).getId())
                        .put("fileKey","photo-a.png").put("capturedAt",clock.instant().toString())
                        .put("expectedVersion",0),"dependsOn",start))
                .add(item(start,"START_EXECUTION").put("obligationId",obligation.getId()));
        MockMultipartFile file=new MockMultipartFile("files","photo-a.png","image/png",png());
        JsonNode response=sync(manifest,ownerToken,file);
        assertThat(response.path("items").get(0).path("resourceType").asText()).isEqualTo("EVIDENCE");
        mvc.perform(request(manifest,supervisorToken,file)).andExpect(status().isForbidden());
    }

    private User user(Role role) {
        User value=users.saveAndFlush(new User("Sync fixture",UUID.randomUUID()+"@example.test",
                passwords.encode("TestPassword123!"),role,1L,1L)); fixtures.add(value); return value;
    }
    private Obligation obligation(EvidenceType type) {
        List<ObligationEvidenceRequirement> requirements=type==null?List.of():List.of(
                new ObligationEvidenceRequirement(SOURCE_REQUIREMENT_IDS.incrementAndGet(),type,true,1,1,1,Map.of()));
        Obligation value=Obligation.configured("Offline work","Sync safely","Site",START,
                START.plusSeconds(3600),1L,1L,List.of(new ObligationStep("Clean","Clean area",1)),requirements);
        value.assignTo(owner.getId()); return obligations.saveAndFlush(value);
    }
    private ObjectNode batch() {
        ObjectNode value=json.createObjectNode().put("batchId",UUID.randomUUID().toString());
        value.putArray("items"); return value;
    }
    private ObjectNode item(UUID id,String type) { return json.createObjectNode().put("clientOperationId",id.toString()).put("type",type); }
    private ObjectNode reference(ObjectNode item,String field,UUID reference) {
        item.withArray(field).add(reference.toString()); return item;
    }
    private JsonNode sync(ObjectNode manifest,String token,MockMultipartFile... files) throws Exception {
        return json.readTree(mvc.perform(request(manifest,token,files)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request(
            ObjectNode manifest,String token,MockMultipartFile... files) throws Exception {
        var request=multipart("/api/v1/sync/batches").file(new MockMultipartFile("manifest","manifest.json",
                MediaType.APPLICATION_JSON_VALUE,json.writeValueAsBytes(manifest))).header("Authorization",token);
        for(MockMultipartFile file:files) request.file(file); return request;
    }
    private static byte[] png() throws Exception {
        var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB); var out=new ByteArrayOutputStream();
        ImageIO.write(image,"png",out); return out.toByteArray();
    }

    @TestConfiguration static class Fixtures {
        @Bean @Primary MutableClock syncClock() { return new MutableClock(); }
        @Bean @Primary TestStorage syncStorage() { return new TestStorage(); }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> value=new AtomicReference<>(START);
        void set(Instant instant) { value.set(instant); }
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return value.get();}
    }
    static class TestStorage implements PhotoStorage {
        private final Map<String,byte[]> values=new ConcurrentHashMap<>();
        public void put(String key,byte[] data,String contentType){values.put(key,data.clone());}
        public byte[] get(String key){return values.get(key).clone();}
        public void delete(String key){values.remove(key);}
    }
}
