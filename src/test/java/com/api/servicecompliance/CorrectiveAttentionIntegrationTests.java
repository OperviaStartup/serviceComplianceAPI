package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.authentication.infrastructure.security.JwtService;
import com.api.servicecompliance.compliance.application.CorrectiveActionService;
import com.api.servicecompliance.compliance.domain.model.ComplianceCase;
import com.api.servicecompliance.compliance.domain.model.ComplianceOutcome;
import com.api.servicecompliance.compliance.domain.model.ComplianceResult;
import com.api.servicecompliance.compliance.domain.repository.ComplianceCaseRepository;
import com.api.servicecompliance.compliance.domain.repository.ComplianceResultRepository;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveActionEvidenceRepository;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveActionRepository;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveActionSubmissionRepository;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveAttentionIdempotencyRepository;
import com.api.servicecompliance.executions.application.PhotoStorage;
import com.api.servicecompliance.executions.application.PhotoStorageUnavailableException;
import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.model.ExecutionResult;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CorrectiveAttentionIntegrationTests.AttentionTestConfiguration.class)
class CorrectiveAttentionIntegrationTests {
    private static final Instant NOW = Instant.parse("2031-03-04T15:00:00Z");

    @Autowired CorrectiveActionService actionsService;
    @Autowired CorrectiveAttentionIdempotencyRepository idempotency;
    @Autowired CorrectiveActionEvidenceRepository correctiveEvidence;
    @Autowired CorrectiveActionSubmissionRepository submissions;
    @Autowired CorrectiveActionRepository actions;
    @Autowired ComplianceResultRepository results;
    @Autowired ComplianceCaseRepository cases;
    @Autowired ExecutionRepository executions;
    @Autowired ObligationRepository obligations;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @Autowired TestStorage storage;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private User supervisor;
    private User operator;
    private User otherOperator;
    private ComplianceResult originalResult;
    private long actionId;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        storage.clear();
        clock.set(NOW);
        supervisor = user("Supervisor", Role.SUPERVISOR);
        operator = user("Operator", Role.OPERATOR);
        otherOperator = user("Other Operator", Role.OPERATOR);
        Obligation obligation = obligations.saveAndFlush(new Obligation(
                "Correct fixture", "Correct fixture", "Site 1", operator.getId(),
                NOW.plusSeconds(3600), 1L, 1L));
        Execution execution = executions.saveAndFlush(new Execution(
                obligation.getId(), operator.getId(), ExecutionResult.COMPLETED, "Submitted facts"));
        ComplianceCase complianceCase = new ComplianceCase(
                obligation.getId(), 1L, 1L, supervisor.getId(), NOW);
        long decisionVersion = complianceCase.appendDecision(NOW);
        complianceCase = cases.saveAndFlush(complianceCase);
        originalResult = results.saveAndFlush(new ComplianceResult(
                complianceCase.getId(), execution.getId(), decisionVersion, null,
                ComplianceOutcome.NON_COMPLIANT, "execution-v0", 0,
                null, null, null, null, "Evidence did not meet the criterion", null,
                supervisor.getId(), NOW, List.of(), List.of()));
        actionId = actionsService.create(complianceCase.getId(),
                new CorrectiveActionService.CreateCorrectiveActionCommand(
                        operator.getId(), "Replace seal", LocalDate.of(2031, 3, 5)), supervisor).id();
    }

    @AfterEach
    void cleanUp() {
        cleanDatabase();
        storage.clear();
    }

    @Test
    void assignedOperatorSubmitsPhotosAndExactReplayDoesNotDuplicateEffects() throws Exception {
        MockMultipartFile file = photo("proof.png");
        MockMultipartFile command = command(0, "Seal replaced", NOW.minusSeconds(30));

        String created = mvc.perform(multipart(
                        "/api/v1/corrective-actions/{id}/attention-submissions", actionId)
                        .file(command).file(file)
                        .header("Authorization", bearer(operator))
                        .header("Idempotency-Key", "attention-0001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submissionNumber").value(1))
                .andExpect(jsonPath("$.actionVersion").value(1))
                .andExpect(jsonPath("$.details").value("Seal replaced"))
                .andExpect(jsonPath("$.evidence[0].contentType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        long submissionId = json.readTree(created).get("id").asLong();

        mvc.perform(multipart("/api/v1/corrective-actions/{id}/attention-submissions", actionId)
                        .file(command(0, "Seal replaced", NOW.minusSeconds(30))).file(photo("proof.png"))
                        .header("Authorization", bearer(operator))
                        .header("Idempotency-Key", "attention-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId));

        assertThat(submissions.count()).isOne();
        assertThat(correctiveEvidence.count()).isOne();
        assertThat(storage.writes()).isEqualTo(1);
        assertThat(actions.findById(actionId).orElseThrow().getState().name())
                .isEqualTo("ATTENTION_SUBMITTED");
        assertThat(results.findById(originalResult.getId()).orElseThrow().getOutcome())
                .isEqualTo(ComplianceOutcome.NON_COMPLIANT);

        mvc.perform(multipart("/api/v1/corrective-actions/{id}/attention-submissions", actionId)
                        .file(command(0, "Different details", NOW.minusSeconds(30))).file(photo("proof.png"))
                        .header("Authorization", bearer(operator))
                        .header("Idempotency-Key", "attention-0001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISMATCH"));
    }

    @Test
    void supervisorReturnsOperatorResubmitsAndSupervisorVerifies() throws Exception {
        long firstEvidence = submit("attention-1001", 0, "Initial correction");

        mvc.perform(post("/api/v1/corrective-actions/{id}/return", actionId)
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new CorrectiveActionService.TransitionCommand(
                                1L, "Operator cannot return"))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/corrective-actions/{id}/return", actionId)
                        .header("Authorization", bearer(supervisor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new CorrectiveActionService.TransitionCommand(
                                1L, "Photo does not show the serial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RETURNED"))
                .andExpect(jsonPath("$.version").value(2));

        submit("attention-1002", 2, "Serial now visible");
        mvc.perform(post("/api/v1/corrective-actions/{id}/verify", actionId)
                        .header("Authorization", bearer(supervisor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new CorrectiveActionService.TransitionCommand(
                                3L, "Correction verified"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.version").value(4));

        mvc.perform(get("/api/v1/corrective-actions/{id}/attention-submissions", actionId)
                        .header("Authorization", bearer(supervisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].submissionNumber").value(1))
                .andExpect(jsonPath("$[1].submissionNumber").value(2));

        mvc.perform(get("/api/v1/corrective-actions/{actionId}/evidence/{evidenceId}/content",
                        actionId, firstEvidence).header("Authorization", bearer(supervisor)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        mvc.perform(get("/api/v1/corrective-actions/{id}/attention-submissions", actionId)
                        .header("Authorization", bearer(otherOperator)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/corrective-actions/{actionId}/evidence/{evidenceId}/content",
                        actionId, firstEvidence).header("Authorization", bearer(otherOperator)))
                .andExpect(status().isForbidden());

        assertThat(submissions.count()).isEqualTo(2);
        assertThat(correctiveEvidence.count()).isEqualTo(2);
        assertThat(results.count()).isOne();
    }

    @Test
    void rejectsUnassignedOperatorBeforeWritingPrivateEvidence() throws Exception {
        mvc.perform(multipart("/api/v1/corrective-actions/{id}/attention-submissions", actionId)
                        .file(command(0, "Unauthorized correction", NOW.minusSeconds(30)))
                        .file(photo("foreign.png"))
                        .header("Authorization", bearer(otherOperator))
                        .header("Idempotency-Key", "attention-2001"))
                .andExpect(status().isForbidden());

        assertThat(submissions.count()).isZero();
        assertThat(correctiveEvidence.count()).isZero();
        assertThat(storage.writes()).isZero();
    }

    private long submit(String key, long version, String details) throws Exception {
        String body = mvc.perform(multipart(
                        "/api/v1/corrective-actions/{id}/attention-submissions", actionId)
                        .file(command(version, details, NOW.minusSeconds(30))).file(photo("proof.png"))
                        .header("Authorization", bearer(operator))
                        .header("Idempotency-Key", key))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("evidence").get(0).path("id").asLong();
    }

    private MockMultipartFile command(long version, String details, Instant capturedAt) throws Exception {
        var value = new com.api.servicecompliance.compliance.application.CorrectiveAttentionService
                .SubmissionCommand(version, details, List.of(capturedAt));
        return new MockMultipartFile("command", "command.json", MediaType.APPLICATION_JSON_VALUE,
                json.writeValueAsBytes(value));
    }

    private MockMultipartFile photo(String name) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x224466);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("files", name, MediaType.IMAGE_PNG_VALUE, output.toByteArray());
    }

    private String bearer(User user) { return "Bearer " + jwt.createToken(user); }

    private User user(String name, Role role) {
        return users.saveAndFlush(new User(name, name.toLowerCase().replace(' ', '-') + "-"
                + UUID.randomUUID() + "@example.test", passwords.encode("Password123!"), role, 1L, 1L));
    }

    private void cleanDatabase() {
        idempotency.deleteAll();
        submissions.deleteAll();
        submissions.flush();
        actions.deleteAll();
        actions.flush();
        results.findAll().stream().sorted(Comparator.comparingLong(
                        ComplianceResult::getDecisionVersion).reversed())
                .forEach(results::delete);
        results.flush();
        cases.deleteAll();
        executions.deleteAll();
        obligations.deleteAll();
        users.deleteAll();
    }

    @TestConfiguration
    static class AttentionTestConfiguration {
        @Bean @Primary MutableClock correctiveAttentionClock() { return new MutableClock(); }
        @Bean @Primary TestStorage correctiveAttentionStorage() { return new TestStorage(); }
    }

    static class MutableClock extends Clock {
        private Instant instant = NOW;
        void set(Instant value) { instant = value; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    static class TestStorage implements PhotoStorage {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        private final AtomicInteger writes = new AtomicInteger();
        @Override public void put(String key, byte[] data, String contentType) {
            if (objects.putIfAbsent(key, data.clone()) != null) throw new PhotoStorageUnavailableException();
            writes.incrementAndGet();
        }
        @Override public byte[] get(String key) {
            byte[] value = objects.get(key);
            if (value == null) throw new PhotoStorageUnavailableException();
            return value.clone();
        }
        @Override public void delete(String key) { objects.remove(key); }
        int writes() { return writes.get(); }
        void clear() { objects.clear(); writes.set(0); }
    }
}
