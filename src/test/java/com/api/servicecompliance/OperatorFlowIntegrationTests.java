package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.executions.application.*;
import com.api.servicecompliance.executions.domain.repository.*;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.jpa.open-in-view=false", "app.field-operations.expiry-enabled=false"})
@AutoConfigureMockMvc
@Import(OperatorFlowIntegrationTests.Fixtures.class)
class OperatorFlowIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObligationRepository obligations;
    @Autowired ExecutionRepository executions;
    @Autowired EvidenceRepository evidence;
    @Autowired ExecutionStepProgressRepository progress;
    @Autowired OperationalExceptionRepository exceptions;
    @Autowired FieldExecutionService flow;
    @Autowired MutableClock clock;
    @Autowired TestPhotos storage;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json=new ObjectMapper();
    private final List<User> fixtureUsers=new ArrayList<>();
    private User owner, other, supervisor, foreign;
    private String ownerToken, supervisorToken;
    private long obligationId;
    private static final Instant START=Instant.parse("2030-01-01T15:00:00Z");
    private static final Instant END=START.plusSeconds(1800);

    @BeforeEach void setUp() throws Exception {
        clock.set(START.plusSeconds(300)); storage.reset();
        try(var c=jdbc.getDataSource().getConnection()) {
            if(c.getMetaData().getDatabaseProductName().contains("PostgreSQL")) {
                jdbc.update("INSERT INTO companies (id,name) VALUES (2,'Test company') ON CONFLICT(id) DO NOTHING");
                jdbc.update("INSERT INTO sites (id,company_id,name) VALUES (2,2,'Foreign site') ON CONFLICT(id) DO NOTHING");
            }
        }
        owner=user(Role.OPERATOR,1L,1L); other=user(Role.OPERATOR,1L,1L);
        supervisor=user(Role.SUPERVISOR,1L,1L); foreign=user(Role.OPERATOR,2L,2L);
        ownerToken=token(owner); supervisorToken=token(supervisor);
        ObjectNode request=json.createObjectNode().put("title","Reception").put("description","Clean reception")
                .put("siteName","Site").put("assignedOperatorId",owner.getId())
                .put("startsAt",START.toString()).put("dueAt",END.toString());
        request.putArray("steps").addObject().put("title","Floor");
        request.withArray("steps").addObject().put("title","Desk");
        request.putArray("photoRequirements").addObject().put("title","Finished floor");
        request.withArray("photoRequirements").addObject().put("title","Finished desk");
        obligationId=response(post("/api/v1/obligations").header("Authorization",supervisorToken)
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()),201).path("id").asLong();
    }

    @AfterEach void clean() {
        for(User user:fixtureUsers) {
            for(var obligation:obligations.findByAssignedOperatorId(user.getId())) {
                for(var execution:executions.findByObligationId(obligation.getId())) {
                    exceptions.deleteAll(exceptions.findByExecutionIdOrderByReportedAtAscIdAsc(execution.getId()));
                    progress.deleteAll(progress.findByExecutionId(execution.getId()));
                    evidence.deleteAll(evidence.findByExecutionId(execution.getId()));
                    executions.delete(execution);
                }
                obligations.delete(obligation);
            }
            var families=jdbc.queryForList("SELECT DISTINCT family_id FROM refresh_sessions WHERE user_id=?",String.class,user.getId());
            jdbc.update("DELETE FROM refresh_sessions WHERE user_id=?",user.getId());
            families.forEach(id -> jdbc.update("DELETE FROM refresh_families WHERE id=?",id));
        }
        users.deleteAll(fixtureUsers); fixtureUsers.clear();
    }

    @Test void completeOnlineJourneyPersistsStepsPhotosAndServerDeterminedResult() throws Exception {
        JsonNode state=start();
        assertThat(state.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(state.path("result").isNull()).isTrue();
        state=step(state,0,true);
        state=step(state,1,true);
        state=upload(state,0,png(0xff0000));
        state=upload(state,1,png(0x0000ff));
        state=submit(state,200);
        assertThat(state.path("state").asText()).isEqualTo("SUBMITTED");
        assertThat(state.path("obligationStatus").asText()).isEqualTo("SUBMITTED");
        assertThat(state.path("result").asText()).isEqualTo("COMPLETED");
        assertThat(state.path("canEdit").asBoolean()).isFalse();
        assertThat(state.toString()).doesNotContain("storageKey","service-key","COMPLIANT");
        assertThat(obligations.findById(obligationId).orElseThrow().getStatus().name()).isEqualTo("SUBMITTED");
        assertThat(current().path("steps").get(0).path("completed").asBoolean()).isTrue();
        JsonNode replay=submit(state,200);
        assertThat(replay.path("version")).isEqualTo(state.path("version"));
        clock.set(END.plusSeconds(60));
        assertThat(current().path("obligationStatus").asText()).isEqualTo("SUBMITTED");
    }

    @Test void missingStepsAndPhotosPreventSubmission() throws Exception {
        JsonNode state=start();
        JsonNode invalid=submit(state,400);
        assertThat(invalid.path("details").has("steps")).isTrue();
        assertThat(invalid.path("details").has("photoRequirements")).isTrue();
        state=step(state,0,true); state=step(state,1,true);
        invalid=submit(state,400);
        assertThat(invalid.path("details").has("photoRequirements")).isTrue();
        assertThat(current().path("state").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test void startIsUniqueRecoverableAndDoesNotDuplicateProgress() throws Exception {
        JsonNode state=start();
        state=step(state,0,true);
        JsonNode replay=response(post(startPath()).header("Authorization",ownerToken),200);
        assertThat(replay.path("id")).isEqualTo(state.path("id"));
        assertThat(replay.path("steps").get(0).path("completed").asBoolean()).isTrue();
        assertThat(executions.findByObligationId(obligationId)).hasSize(1);
        assertThat(progress.findByExecutionId(state.path("id").asLong())).hasSize(2);
    }

    @Test void rejectsEarlyStartAndMutationsAtTheExactDeadline() throws Exception {
        clock.set(START.minusNanos(1));
        assertThat(response(post(startPath()).header("Authorization",ownerToken),409).path("code").asText())
                .isEqualTo("EXECUTION_NOT_STARTED_YET");
        clock.set(START); JsonNode state=start();
        clock.set(END);
        JsonNode invalid=stepRequest(state,0,true,state.path("version").asLong(),409);
        assertThat(invalid.path("code").asText()).isEqualTo("OBLIGATION_OVERDUE");
        assertThat(submit(state,409).path("code").asText()).isEqualTo("OBLIGATION_OVERDUE");
        assertThat(current().path("obligationStatus").asText()).isEqualTo("OVERDUE");
        assertThat(response(get("/api/v1/obligations/"+obligationId).header("Authorization",ownerToken),200)
                .path("status").asText()).isEqualTo("OVERDUE");
        assertThat(current().path("canEdit").asBoolean()).isFalse();
        assertThat(progress.findByExecutionId(state.path("id").asLong())).allMatch(p -> !p.isCompleted());
        assertThat(obligationsExpire()).isEqualTo(1);
        assertThat(obligations.findById(obligationId).orElseThrow().getStatus().name()).isEqualTo("OVERDUE");
    }

    @Test void rejectsStartingAnUnstartedExpiredObligation() throws Exception {
        clock.set(END);
        assertThat(response(post(startPath()).header("Authorization",ownerToken),409).path("code").asText())
                .isEqualTo("OBLIGATION_OVERDUE");
        assertThat(executions.findByObligationId(obligationId)).isEmpty();
    }

    @Test void staleChangesReturnTheCurrentVersionAndRepeatedStepDoesNotRewriteIt() throws Exception {
        JsonNode initial=start(); JsonNode changed=step(initial,0,true);
        JsonNode invalid=stepRequest(changed,1,true,initial.path("version").asLong(),409);
        assertThat(invalid.path("code").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(invalid.path("currentVersion")).isEqualTo(changed.path("version"));
        JsonNode replay=stepRequest(initial,0,true,initial.path("version").asLong(),200);
        assertThat(replay.path("version")).isEqualTo(changed.path("version"));
        JsonNode unmarked=step(changed,0,false);
        assertThat(unmarked.path("steps").get(0).path("completed").asBoolean()).isFalse();
    }

    @Test void exceptionIsSeparateIdempotentAndAllowsContinuationWithoutWaivingRequirements() throws Exception {
        JsonNode state=start(); UUID operation=UUID.randomUUID();
        ObjectNode command=exceptionCommand(state,operation);
        JsonNode reported=response(post(exceptionPath(state)).header("Authorization",ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(command.toString()),200);
        assertThat(reported.path("exceptions").size()).isEqualTo(1);
        assertThat(reported.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(reported.path("result").isNull()).isTrue();
        JsonNode replay=response(post(exceptionPath(state)).header("Authorization",ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(command.toString()),200);
        assertThat(replay.path("version")).isEqualTo(reported.path("version"));
        command.put("description","Changed payload");
        assertThat(response(post(exceptionPath(state)).header("Authorization",ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(command.toString()),409).path("code").asText())
                .isEqualTo("IDEMPOTENCY_KEY_MISMATCH");
        assertThat(submit(reported,400).path("details").has("photoRequirements")).isTrue();
        state=step(reported,0,true); state=step(state,1,true);
        state=upload(state,0,png(1)); state=upload(state,1,png(2));
        assertThat(submit(state,200).path("exceptions").size()).isEqualTo(1);
    }

    @Test void photoCannotBeReplacedAndExactRetryDoesNotUploadTwice() throws Exception {
        JsonNode initial=start(); byte[] image=png(1);
        JsonNode attached=upload(initial,0,image);
        JsonNode replay=response(photoRequest(initial,0,image,"image/png"),200);
        assertThat(replay.path("version")).isEqualTo(attached.path("version"));
        assertThat(storage.writes.get()).isEqualTo(1);
        assertThat(response(photoRequest(attached,0,png(2),"image/png"),409).path("code").asText())
                .isEqualTo("PHOTO_ALREADY_ATTACHED");
        assertThat(evidence.findByExecutionId(initial.path("id").asLong())).hasSize(1);
    }

    @Test void photoDownloadRequiresScopeAndOwnershipAndVerifiesIntegrity() throws Exception {
        byte[] image=png(1); JsonNode state=upload(start(),0,image);
        String path=state.path("photoRequirements").get(0).path("contentPath").asText();
        mvc.perform(get(path).header("Authorization",ownerToken)).andExpect(status().isOk())
                .andExpect(content().bytes(image)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(path).header("Authorization",supervisorToken)).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization",token(other))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization",token(foreign))).andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        storage.corrupt=true;
        mvc.perform(get(path).header("Authorization",ownerToken)).andExpect(status().isServiceUnavailable());
    }

    @Test void invalidPhotosAndFailedStorageDoNotSatisfyARequirement() throws Exception {
        JsonNode state=start();
        response(photoRequest(state,0,"not an image".getBytes(),"image/png"),400);
        response(photoRequest(state,0,png(1),"image/jpeg"),400);
        response(photoRequest(state,0,new byte[PhotoValidator.MAX_BYTES+1],"image/png"),400);
        assertThat(storage.writes.get()).isZero();
        storage.failAfterWrite=true;
        assertThat(response(photoRequest(state,0,png(1),"image/png"),503).path("code").asText())
                .isEqualTo("PHOTO_STORAGE_UNAVAILABLE");
        assertThat(evidence.findByExecutionId(state.path("id").asLong())).isEmpty();
        assertThat(storage.objects).isEmpty();
        assertThat(current().path("version")).isEqualTo(state.path("version"));
    }

    @Test void acceptsAnActualJpegAndServesItsVerifiedContentType() throws Exception {
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);
        var out=new ByteArrayOutputStream(); assertThat(ImageIO.write(image,"jpeg",out)).isTrue();
        byte[] data=out.toByteArray(); JsonNode state=start();
        state=response(photoRequest(state,0,data,"image/jpeg"),200);
        mvc.perform(get(state.path("photoRequirements").get(0).path("contentPath").asText())
                        .header("Authorization",ownerToken))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(data));
    }

    @Test void anUploadFinishingAfterTheDeadlineIsCompensated() throws Exception {
        JsonNode state=start(); storage.afterPut=() -> clock.set(END);
        assertThat(response(photoRequest(state,0,png(1),"image/png"),409).path("code").asText())
                .isEqualTo("OBLIGATION_OVERDUE");
        assertThat(storage.objects).isEmpty();
        assertThat(evidence.findByExecutionId(state.path("id").asLong())).isEmpty();
    }

    @Test void protectsMutationsAndDoesNotAllowForeignStepsOrPhotoRequirements() throws Exception {
        response(post(startPath()).header("Authorization",supervisorToken),403);
        response(post(startPath()).header("Authorization",token(other)),403);
        response(post(startPath()).header("Authorization",token(foreign)),404);
        JsonNode state=start();
        response(put("/api/v1/executions/"+state.path("id").asLong()+"/steps/9223372036854775807")
                .header("Authorization",ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true,\"expectedVersion\":0}"),404);
        var invalid=multipart("/api/v1/executions/"+state.path("id").asLong()+"/photo-requirements/9223372036854775807/photo")
                .file(new MockMultipartFile("file","photo.png","image/png",png(1))).param("expectedVersion","0");
        response(invalid.header("Authorization",ownerToken),404);
        assertThat(storage.writes.get()).isZero();
    }

    @Test void legacyRoutesCannotBypassTheConfiguredFlow() throws Exception {
        response(post("/api/v1/executions").header("Authorization",ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"obligationId\":"+obligationId+",\"result\":\"COMPLETED\"}"),409);
        JsonNode state=start();
        assertThat(response(post("/api/v1/executions/"+state.path("id").asLong()+"/evidence")
                .header("Authorization",ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PHOTO\",\"url\":\"https://example.test/not-uploaded.png\"}"),409)
                .path("code").asText()).isEqualTo("PHOTO_UPLOAD_REQUIRED");
        assertThat(evidence.findByExecutionId(state.path("id").asLong())).isEmpty();
    }

    @Test void submittedExecutionRejectsFurtherStepOrExceptionChanges() throws Exception {
        JsonNode state=start(); state=step(state,0,true); state=step(state,1,true);
        state=upload(state,0,png(1)); state=upload(state,1,png(2)); state=submit(state,200);
        assertThat(stepRequest(state,0,false,state.path("version").asLong(),409).path("code").asText())
                .isEqualTo("EXECUTION_ALREADY_SUBMITTED");
        response(post(exceptionPath(state)).header("Authorization",ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(exceptionCommand(state,UUID.randomUUID()).toString()),409);
    }

    @Test void concurrentStartsCreateOneExecution() throws Exception {
        var ids=concurrently(() -> flow.start(obligationId,owner).execution().id(),
                () -> flow.start(obligationId,owner).execution().id());
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(executions.findByObligationId(obligationId)).hasSize(1);
    }

    @Test void invalidExceptionReasonsAndMissingMultipartParametersReturnControlledErrors() throws Exception {
        JsonNode state=start(); ObjectNode command=exceptionCommand(state,UUID.randomUUID());
        command.put("reason","NOT_ALLOWED");
        response(post(exceptionPath(state)).header("Authorization",ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(command.toString()),400);
        var missingVersion=multipart("/api/v1/executions/"+state.path("id").asLong()+"/photo-requirements/"
                +state.path("photoRequirements").get(0).path("id").asLong()+"/photo")
                .file(new MockMultipartFile("file","photo.png","image/png",png(1)))
                .header("Authorization",ownerToken);
        assertThat(response(missingVersion,400).path("code").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(exceptions.findByExecutionIdOrderByReportedAtAscIdAsc(state.path("id").asLong())).isEmpty();
    }

    @Test void anExplicitObligationWithoutPhotosStillRequiresItsSteps() throws Exception {
        ObjectNode request=json.createObjectNode().put("title","Instructions only").put("description","No photos")
                .put("siteName","Site").put("assignedOperatorId",owner.getId())
                .put("startsAt",START.toString()).put("dueAt",END.toString());
        request.putArray("steps").addObject().put("title","Perform activity");
        request.putArray("photoRequirements");
        obligationId=response(post("/api/v1/obligations").header("Authorization",supervisorToken)
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()),201).path("id").asLong();
        JsonNode state=start(); submit(state,400); state=step(state,0,true);
        assertThat(submit(state,200).path("result").asText()).isEqualTo("COMPLETED");
    }

    @Test void concurrentDifferentPhotosNeverReplaceTheFirstOne() throws Exception {
        JsonNode state=start(); long id=state.path("id").asLong(), requirement=state.path("photoRequirements").get(0).path("id").asLong();
        var outcomes=concurrently(() -> directUpload(id,requirement,png(1)), () -> directUpload(id,requirement,png(2)));
        assertThat(outcomes).containsExactlyInAnyOrder("OK","PHOTO_ALREADY_ATTACHED");
        assertThat(storage.writes.get()).isEqualTo(1);
        assertThat(evidence.findByExecutionId(id)).hasSize(1);
    }

    private int obligationsExpire() {
        return new org.springframework.transaction.support.TransactionTemplate(
                transactionManager).execute(status -> obligations.expireFieldObligations(clock.instant()));
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    private String directUpload(long id,long requirement,byte[] bytes) {
        try { flow.attachPhoto(id,requirement,new MockMultipartFile("file","photo.png","image/png",bytes),0,null,owner); return "OK"; }
        catch(ApiConflictException exception) { return exception.getCode(); }
    }
    private <T> List<T> concurrently(Callable<T> first,Callable<T> second) throws Exception {
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1); var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<T> a=() -> { ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return first.call(); };
            Callable<T> b=() -> { ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return second.call(); };
            var x=executor.submit(a); var y=executor.submit(b); assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    private User user(Role role,long company,long site) {
        User user=users.save(new User("Flow fixture",UUID.randomUUID()+"@example.test",passwords.encode("TestPassword123!"),role,company,site));
        fixtureUsers.add(user); return user;
    }
    private String token(User user) throws Exception {
        return "Bearer "+response(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().put("identifier",user.getEmail()).put("password","TestPassword123!").toString()),200).path("token").asText();
    }
    private String startPath() { return "/api/v1/obligations/"+obligationId+"/execution"; }
    private JsonNode start() throws Exception { return response(post(startPath()).header("Authorization",ownerToken),201); }
    private JsonNode current() throws Exception { return response(get(startPath()).header("Authorization",ownerToken),200); }
    private JsonNode step(JsonNode state,int index,boolean completed) throws Exception { return stepRequest(state,index,completed,state.path("version").asLong(),200); }
    private JsonNode stepRequest(JsonNode state,int index,boolean completed,long version,int status) throws Exception {
        return response(put("/api/v1/executions/"+state.path("id").asLong()+"/steps/"+state.path("steps").get(index).path("id").asLong())
                .header("Authorization",ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.createObjectNode().put("completed",completed).put("expectedVersion",version).toString()),status);
    }
    private MockMultipartHttpServletRequestBuilder photoRequest(JsonNode state,int index,byte[] image,String type) {
        return multipart("/api/v1/executions/"+state.path("id").asLong()+"/photo-requirements/"+state.path("photoRequirements").get(index).path("id").asLong()+"/photo")
                .file(new MockMultipartFile("file","photo.png",type,image)).param("expectedVersion",state.path("version").asText())
                .header("Authorization",ownerToken);
    }
    private JsonNode upload(JsonNode state,int index,byte[] bytes) throws Exception { return response(photoRequest(state,index,bytes,"image/png"),200); }
    private JsonNode submit(JsonNode state,int status) throws Exception {
        return response(post("/api/v1/executions/"+state.path("id").asLong()+"/submit").header("Authorization",ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.createObjectNode().put("expectedVersion",state.path("version").asLong()).toString()),status);
    }
    private String exceptionPath(JsonNode state) { return "/api/v1/executions/"+state.path("id").asLong()+"/exceptions"; }
    private ObjectNode exceptionCommand(JsonNode state,UUID operation) {
        return json.createObjectNode().put("reason","AREA_CLOSED").put("description","Door is locked")
                .put("stepId",state.path("steps").get(0).path("id").asLong()).put("operationId",operation.toString())
                .put("expectedVersion",state.path("version").asLong());
    }
    private JsonNode response(RequestBuilder request,int expectedStatus) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }
    private static byte[] png(int color) throws Exception {
        var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB); image.setRGB(0,0,color);
        var out=new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
    }

    @TestConfiguration static class Fixtures {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean @Primary TestPhotos testPhotos() { return new TestPhotos(); }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now=new AtomicReference<>(START);
        void set(Instant value) { now.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
    static class TestPhotos implements PhotoStorage {
        final Map<String,byte[]> objects=new ConcurrentHashMap<>(); final AtomicInteger writes=new AtomicInteger();
        boolean failAfterWrite,corrupt; Runnable afterPut=() -> {};
        void reset() { objects.clear(); writes.set(0); failAfterWrite=false; corrupt=false; afterPut=() -> {}; }
        @Override public void put(String key,byte[] data,String type) { writes.incrementAndGet(); objects.put(key,data.clone()); afterPut.run(); if(failAfterWrite) throw new PhotoStorageUnavailableException(); }
        @Override public byte[] get(String key) { if(corrupt) return new byte[]{1}; byte[] data=objects.get(key); if(data==null) throw new PhotoStorageUnavailableException(); return data.clone(); }
        @Override public void delete(String key) { objects.remove(key); }
    }
}
