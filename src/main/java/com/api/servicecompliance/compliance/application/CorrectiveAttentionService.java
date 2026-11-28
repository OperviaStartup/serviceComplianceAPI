package com.api.servicecompliance.compliance.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.compliance.domain.model.CorrectiveAction;
import com.api.servicecompliance.compliance.domain.model.CorrectiveActionEvidence;
import com.api.servicecompliance.compliance.domain.model.CorrectiveActionState;
import com.api.servicecompliance.compliance.domain.model.CorrectiveActionSubmission;
import com.api.servicecompliance.compliance.domain.model.CorrectiveAttentionIdempotency;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveActionEvidenceRepository;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveActionRepository;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveActionSubmissionRepository;
import com.api.servicecompliance.compliance.domain.repository.CorrectiveAttentionIdempotencyRepository;
import com.api.servicecompliance.executions.application.PhotoStorage;
import com.api.servicecompliance.executions.application.PhotoStorageUnavailableException;
import com.api.servicecompliance.executions.application.PhotoValidator;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Validated
public class CorrectiveAttentionService {
    private static final Logger LOG = LoggerFactory.getLogger(CorrectiveAttentionService.class);
    private static final String SUBMIT_ATTENTION = "SUBMIT_CORRECTIVE_ATTENTION";
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(7);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");

    private final CorrectiveActionRepository actions;
    private final CorrectiveActionSubmissionRepository submissions;
    private final CorrectiveActionEvidenceRepository evidence;
    private final CorrectiveAttentionIdempotencyRepository idempotency;
    private final PhotoStorage storage;
    private final PhotoValidator images;
    private final Clock clock;

    public CorrectiveAttentionService(CorrectiveActionRepository actions,
                                      CorrectiveActionSubmissionRepository submissions,
                                      CorrectiveActionEvidenceRepository evidence,
                                      CorrectiveAttentionIdempotencyRepository idempotency,
                                      PhotoStorage storage, PhotoValidator images, Clock clock) {
        this.actions = actions;
        this.submissions = submissions;
        this.evidence = evidence;
        this.idempotency = idempotency;
        this.storage = storage;
        this.images = images;
        this.clock = clock;
    }

    @Transactional
    public SubmissionResult submit(Long actionId, String idempotencyKey,
                                   @Valid SubmissionCommand command,
                                   List<MultipartFile> files, User operator) {
        requireActive(operator);
        validateIdempotencyKey(idempotencyKey);
        List<ValidatedEvidence> validated = validateEvidence(files, command.capturedAt());
        String details = command.details().trim();
        String requestHash = requestHash(actionId, details, validated);

        CorrectiveAction action = actions.lockInScope(actionId, operator.getCompanyId(), operator.getSiteId())
                .orElseThrow(CorrectiveAttentionService::notFound);
        requireAssignedOperator(action, operator);
        idempotency.deleteByExpiresAtBefore(clock.instant());
        var replay = idempotency.findByCompanyIdAndActorIdAndCommandAndIdempotencyKey(
                operator.getCompanyId(), operator.getId(), SUBMIT_ATTENTION, idempotencyKey);
        if (replay.isPresent()) {
            if (!replay.get().getRequestHash().equals(requestHash)) {
                throw conflict("IDEMPOTENCY_KEY_MISMATCH",
                        "La clave ya fue usada con otra solicitud", action.getVersion());
            }
            CorrectiveActionSubmission stored = submissions.findById(replay.get().getSubmissionId())
                    .orElseThrow(CorrectiveAttentionService::notFound);
            return new SubmissionResult(response(stored), false);
        }

        verifyExpectedVersion(action, command.expectedVersion());
        if (action.getState() != CorrectiveActionState.ASSIGNED
                && action.getState() != CorrectiveActionState.RETURNED) {
            throw conflict("CORRECTIVE_ACTION_INVALID_STATE",
                    "La acción correctiva no admite un nuevo envío", action.getVersion());
        }

        Instant now = clock.instant();
        List<CorrectiveActionEvidence> storedEvidence = storeEvidence(action, validated, operator, now);
        try {
            action.submitAttention(operator.getId(), details, now);
        } catch (IllegalStateException exception) {
            throw conflict("CORRECTIVE_ACTION_INVALID_STATE",
                    "La acción correctiva no admite un nuevo envío", action.getVersion());
        }
        action = actions.saveAndFlush(action);
        int submissionNumber = Math.toIntExact(submissions.countByCorrectiveActionId(actionId) + 1);
        CorrectiveActionSubmission submission = submissions.saveAndFlush(new CorrectiveActionSubmission(
                actionId, submissionNumber, action.getVersion(), details, operator.getId(), now,
                storedEvidence));
        idempotency.saveAndFlush(new CorrectiveAttentionIdempotency(
                operator.getCompanyId(), operator.getId(), SUBMIT_ATTENTION, idempotencyKey,
                requestHash, submission.getId(), now, now.plus(IDEMPOTENCY_RETENTION)));
        return new SubmissionResult(response(submission), true);
    }

    @Transactional(readOnly = true)
    public List<AttentionSubmissionResponse> list(Long actionId, User actor) {
        CorrectiveAction action = actionForRead(actionId, actor);
        return submissions.findAllByCorrectiveActionIdOrderBySubmissionNumberAsc(action.getId())
                .stream().map(CorrectiveAttentionService::response).toList();
    }

    @Transactional(readOnly = true)
    public EvidenceContent content(Long actionId, Long evidenceId, User actor) {
        actionForRead(actionId, actor);
        CorrectiveActionEvidence stored = evidence.findByIdAndCorrectiveActionId(evidenceId, actionId)
                .orElseThrow(CorrectiveAttentionService::notFound);
        byte[] data = storage.get(stored.getStorageKey());
        if (data.length != stored.getFileSize()
                || !PhotoValidator.checksum(data).equals(stored.getChecksum())) {
            throw new PhotoStorageUnavailableException();
        }
        return new EvidenceContent(stored.getContentType(), data);
    }

    private CorrectiveAction actionForRead(Long actionId, User actor) {
        requireActive(actor);
        CorrectiveAction action = actions.findByIdAndCompanyIdAndSiteId(
                        actionId, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(CorrectiveAttentionService::notFound);
        if (actor.getRole() == Role.OPERATOR && !Objects.equals(action.getAssigneeId(), actor.getId())) {
            throw new AccessDeniedException("Corrective action is assigned to another operator");
        }
        if (actor.getRole() != Role.OPERATOR && actor.getRole() != Role.SUPERVISOR) {
            throw new AccessDeniedException("Corrective action role required");
        }
        return action;
    }

    private List<ValidatedEvidence> validateEvidence(List<MultipartFile> files, List<Instant> capturedAt) {
        if (files == null || files.isEmpty() || files.size() > 5) {
            throw validation("files", "debe incluir entre 1 y 5 fotografías");
        }
        if (capturedAt == null || capturedAt.size() != files.size()) {
            throw validation("capturedAt", "debe incluir una fecha de captura por fotografía");
        }
        Instant now = clock.instant();
        List<ValidatedEvidence> result = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            Instant captured = capturedAt.get(index);
            if (captured == null || captured.isAfter(now)) {
                throw validation("capturedAt", "no puede ser nula ni futura");
            }
            result.add(new ValidatedEvidence(images.validate(files.get(index)), captured));
        }
        return result;
    }

    private List<CorrectiveActionEvidence> storeEvidence(CorrectiveAction action,
                                                          List<ValidatedEvidence> validated,
                                                          User operator, Instant now) {
        List<CorrectiveActionEvidence> stored = new ArrayList<>();
        for (int index = 0; index < validated.size(); index++) {
            ValidatedEvidence item = validated.get(index);
            String key = "company/" + action.getCompanyId() + "/site/" + action.getSiteId()
                    + "/corrective-action/" + action.getId() + "/" + UUID.randomUUID()
                    + "." + item.photo().extension();
            compensateStorageOnRollback(key);
            storage.put(key, item.photo().data(), item.photo().contentType());
            stored.add(new CorrectiveActionEvidence(action.getId(), index + 1, key,
                    item.photo().contentType(), item.photo().data().length, item.photo().checksum(),
                    item.capturedAt(), operator.getId(), now));
        }
        return stored;
    }

    private void compensateStorageOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException cleanupFailure) {
                        LOG.warn("A corrective evidence upload requires cleanup");
                    }
                } else if (status == STATUS_UNKNOWN) {
                    LOG.warn("A corrective evidence transaction requires reconciliation");
                }
            }
        });
    }

    private static AttentionSubmissionResponse response(CorrectiveActionSubmission submission) {
        return new AttentionSubmissionResponse(submission.getId(), submission.getCorrectiveActionId(),
                submission.getSubmissionNumber(), submission.getActionVersion(), submission.getDetails(),
                submission.getSubmittedBy(), submission.getSubmittedAt(), submission.getEvidence().stream()
                .map(item -> new CorrectiveEvidenceResponse(item.getId(), item.getEvidenceOrder(),
                        item.getContentType(), item.getFileSize(), item.getChecksum(), item.getCapturedAt(),
                        item.getCreatedBy(), item.getCreatedAt(), "/api/v1/corrective-actions/"
                        + submission.getCorrectiveActionId() + "/evidence/" + item.getId() + "/content"))
                .toList());
    }

    private static String requestHash(Long actionId, String details, List<ValidatedEvidence> evidence) {
        StringBuilder canonical = new StringBuilder().append(actionId).append('|').append(details);
        evidence.forEach(item -> canonical.append('|').append(item.photo().checksum())
                .append('|').append(item.capturedAt()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireActive(User actor) {
        if (actor == null || !actor.isActive()) throw new AccessDeniedException("Active account required");
    }

    private static void requireAssignedOperator(CorrectiveAction action, User actor) {
        if (actor.getRole() != Role.OPERATOR || !Objects.equals(action.getAssigneeId(), actor.getId())) {
            throw new AccessDeniedException("Assigned operator required");
        }
    }

    private static void verifyExpectedVersion(CorrectiveAction action, long expectedVersion) {
        if (action.getVersion() != expectedVersion) {
            throw conflict("VERSION_CONFLICT",
                    "La acción correctiva cambió desde la última lectura", action.getVersion());
        }
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw validation("idempotencyKey",
                    "debe contener entre 8 y 100 caracteres alfanuméricos o . _ : -");
        }
    }

    private static ApiConflictException conflict(String code, String message, long version) {
        return new ApiConflictException(code, message, version);
    }

    private static FieldValidationException validation(String field, String message) {
        return new FieldValidationException("Envío correctivo inválido", Map.of(field, List.of(message)));
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Acción correctiva o evidencia no encontrada");
    }

    public record SubmissionCommand(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 2000) String details,
            @NotNull @Size(min = 1, max = 5) List<@NotNull Instant> capturedAt) {}

    public record SubmissionResult(AttentionSubmissionResponse submission, boolean created) {}

    public record AttentionSubmissionResponse(
            Long id, Long correctiveActionId, int submissionNumber, long actionVersion,
            String details, Long submittedBy, Instant submittedAt,
            List<CorrectiveEvidenceResponse> evidence) {}

    public record CorrectiveEvidenceResponse(
            Long id, int order, String contentType, long fileSize, String checksum,
            Instant capturedAt, Long createdBy, Instant createdAt, String contentPath) {}

    public record EvidenceContent(String contentType, byte[] data) {}

    private record ValidatedEvidence(PhotoValidator.ValidatedPhoto photo, Instant capturedAt) {}
}
