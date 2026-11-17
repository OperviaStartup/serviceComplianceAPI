package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.executions.domain.model.Evidence;
import com.api.servicecompliance.executions.domain.model.EvidenceIdempotency;
import com.api.servicecompliance.executions.domain.model.EvidenceType;
import com.api.servicecompliance.executions.domain.model.Execution;
import com.api.servicecompliance.executions.domain.model.ExecutionState;
import com.api.servicecompliance.executions.domain.repository.EvidenceIdempotencyRepository;
import com.api.servicecompliance.executions.domain.repository.EvidenceRepository;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.model.ObligationEvidenceRequirement;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.Role;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class RequiredEvidenceService {
    private static final Logger LOG = LoggerFactory.getLogger(RequiredEvidenceService.class);
    private static final String ATTACH_EVIDENCE = "ATTACH_EVIDENCE";
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(7);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    private final ObligationRepository obligations;
    private final ExecutionRepository executions;
    private final EvidenceRepository evidence;
    private final EvidenceIdempotencyRepository idempotency;
    private final UserRepository users;
    private final PhotoStorage storage;
    private final PhotoValidator images;
    private final Clock clock;

    public RequiredEvidenceService(ObligationRepository obligations, ExecutionRepository executions,
                                   EvidenceRepository evidence, EvidenceIdempotencyRepository idempotency,
                                   UserRepository users, PhotoStorage storage, PhotoValidator images, Clock clock) {
        this.obligations = obligations;
        this.executions = executions;
        this.evidence = evidence;
        this.idempotency = idempotency;
        this.users = users;
        this.storage = storage;
        this.images = images;
        this.clock = clock;
    }

    public EvidenceView attachBinary(Long executionId, Long requirementId, String idempotencyKey,
                                     MultipartFile file, long expectedVersion, Instant capturedAt,
                                     BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                                     User actor) {
        lockActor(actor);
        Context context = context(executionId, requirementId, actor);
        EvidenceType type = context.requirement().getEvidenceType();
        if (type != EvidenceType.PHOTO && type != EvidenceType.SIGNATURE) {
            throw validation("file", "el requisito no admite archivos binarios");
        }
        validateIdempotencyKey(idempotencyKey);
        PhotoValidator.ValidatedPhoto image = images.validate(file, type);
        validateCapture(context.requirement(), capturedAt, latitude, longitude, accuracyMeters);
        String requestHash = hash(executionId + "|" + requirementId + "|" + image.checksum() + "|"
                + capturedAt + "|" + decimal(latitude) + "|" + decimal(longitude) + "|"
                + decimal(accuracyMeters));
        Optional<Evidence> replay = replay(actor, idempotencyKey, requestHash);
        if (replay.isPresent()) return view(replay.get());

        validateMaximumAge(context.requirement(), capturedAt);
        validateWritable(context, expectedVersion);
        validateMaximum(context);
        String key = "company/" + context.obligation().getCompanyId() + "/site/"
                + context.obligation().getSiteId() + "/execution/" + executionId + "/requirement/"
                + requirementId + "/" + UUID.randomUUID() + "." + image.extension();
        compensateStorageOnRollback(key);
        storage.put(key, image.data(), image.contentType());
        Instant storedAt = clock.instant();
        ensureWindow(context, storedAt);
        Evidence stored = evidence.saveAndFlush(Evidence.binary(executionId, requirementId, type, actor.getId(),
                key, image.contentType(), image.data().length, image.checksum(), latitude, longitude,
                accuracyMeters, capturedAt, storedAt));
        context.execution().evidenceAttached(storedAt);
        record(actor, idempotencyKey, requestHash, stored.getId(), storedAt);
        executions.flush();
        return view(stored);
    }

    public EvidenceView attachStructured(Long executionId, Long requirementId, String idempotencyKey,
                                         StructuredEvidenceCommand command, User actor) {
        lockActor(actor);
        Context context = context(executionId, requirementId, actor);
        EvidenceType type = context.requirement().getEvidenceType();
        if (type != EvidenceType.QR_CODE && type != EvidenceType.LOCATION) {
            throw validation("evidence", "el requisito requiere un archivo binario");
        }
        validateIdempotencyKey(idempotencyKey);
        validateCapture(context.requirement(), command.capturedAt(), command.latitude(), command.longitude(),
                command.accuracyMeters());
        String value = validateStructured(context.requirement(), command);
        String requestHash = hash(executionId + "|" + requirementId + "|" + value + "|"
                + command.capturedAt() + "|" + decimal(command.latitude()) + "|"
                + decimal(command.longitude()) + "|" + decimal(command.accuracyMeters()));
        Optional<Evidence> replay = replay(actor, idempotencyKey, requestHash);
        if (replay.isPresent()) return view(replay.get());

        validateMaximumAge(context.requirement(), command.capturedAt());
        validateWritable(context, command.expectedVersion());
        validateMaximum(context);
        Instant storedAt = clock.instant();
        String integrityPayload = type == EvidenceType.QR_CODE ? value
                : decimal(command.latitude()) + "|" + decimal(command.longitude()) + "|"
                + decimal(command.accuracyMeters());
        byte[] integrityBytes = integrityPayload.getBytes(StandardCharsets.UTF_8);
        Evidence stored = type == EvidenceType.QR_CODE
                ? Evidence.qrCode(executionId, requirementId, actor.getId(), value,
                        integrityBytes.length, hash(integrityPayload), command.capturedAt(), storedAt)
                : Evidence.location(executionId, requirementId, actor.getId(), command.latitude(),
                        command.longitude(), command.accuracyMeters(), integrityBytes.length,
                        hash(integrityPayload), command.capturedAt(), storedAt);
        stored = evidence.saveAndFlush(stored);
        context.execution().evidenceAttached(storedAt);
        record(actor, idempotencyKey, requestHash, stored.getId(), storedAt);
        executions.flush();
        return view(stored);
    }

    private String validateStructured(ObligationEvidenceRequirement requirement,
                                      StructuredEvidenceCommand command) {
        if (requirement.getEvidenceType() == EvidenceType.QR_CODE) {
            String value = command.textValue() == null ? "" : command.textValue().trim();
            if (value.isEmpty() || value.length() > 512) {
                throw validation("textValue", "debe contener entre 1 y 512 caracteres");
            }
            String prefix = requirement.getConstraints().get("expectedPrefix");
            if (prefix != null && !value.startsWith(prefix)) {
                throw validation("textValue", "no coincide con el prefijo esperado");
            }
            if (command.latitude() != null || command.longitude() != null || command.accuracyMeters() != null) {
                throw validation("location", "no corresponde a un requisito QR_CODE");
            }
            return value;
        }
        if (command.textValue() != null && !command.textValue().isBlank()) {
            throw validation("textValue", "no corresponde a un requisito LOCATION");
        }
        BigDecimal maximumAccuracy = new BigDecimal(
                requirement.getConstraints().getOrDefault("maxAccuracyMeters", "1000"));
        if (command.accuracyMeters().compareTo(maximumAccuracy) > 0) {
            throw validation("accuracyMeters", "supera la precisión máxima permitida");
        }
        return "LOCATION";
    }

    private void validateCapture(ObligationEvidenceRequirement requirement, Instant capturedAt,
                                 BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {
        var errors = new LinkedHashMap<String, List<String>>();
        Instant now = clock.instant();
        if (capturedAt == null) errors.put("capturedAt", List.of("es obligatorio"));
        else {
            if (capturedAt.isAfter(now)) errors.put("capturedAt", List.of("no puede ser futura"));
        }
        boolean anyLocation = latitude != null || longitude != null || accuracyMeters != null;
        boolean locationRequired = requirement.getEvidenceType() == EvidenceType.LOCATION
                || Boolean.parseBoolean(requirement.getConstraints().getOrDefault("requireGeolocation", "false"));
        if (locationRequired || anyLocation) {
            if (latitude == null || latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
                errors.put("latitude", List.of("debe estar entre -90 y 90"));
            }
            if (longitude == null || longitude.compareTo(MIN_LONGITUDE) < 0
                    || longitude.compareTo(MAX_LONGITUDE) > 0) {
                errors.put("longitude", List.of("debe estar entre -180 y 180"));
            }
            if (accuracyMeters == null || accuracyMeters.compareTo(BigDecimal.ZERO) <= 0) {
                errors.put("accuracyMeters", List.of("debe ser mayor que 0"));
            }
        }
        if (!errors.isEmpty()) throw new FieldValidationException("Evidencia inválida", errors);
    }

    private void validateMaximumAge(ObligationEvidenceRequirement requirement, Instant capturedAt) {
        String maximumAge = requirement.getConstraints().get("maxAgeMinutes");
        if (maximumAge != null && capturedAt.isBefore(
                clock.instant().minus(Duration.ofMinutes(Long.parseLong(maximumAge))))) {
            throw validation("capturedAt", "supera la antigüedad máxima permitida");
        }
    }

    private Context context(Long executionId, Long requirementId, User actor) {
        if (actor == null || actor.getRole() != Role.OPERATOR || !actor.isActive()) {
            throw new AccessDeniedException("Operator role required");
        }
        Long obligationId = executions.findObligationId(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Ejecución no encontrada"));
        Obligation obligation = obligations.lockInScope(obligationId, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(() -> new ResourceNotFoundException("Ejecución no encontrada"));
        if (!Objects.equals(obligation.getAssignedOperatorId(), actor.getId())) {
            throw new AccessDeniedException("Forbidden");
        }
        Execution execution = executions.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Ejecución no encontrada"));
        if (!Objects.equals(execution.getOperatorId(), actor.getId())) {
            throw new AccessDeniedException("Forbidden");
        }
        ObligationEvidenceRequirement requirement = obligation.getEvidenceRequirements().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), requirementId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException(
                        "Requisito de evidencia no encontrado en esta obligación"));
        return new Context(obligation, execution, requirement);
    }

    private void validateWritable(Context context, long expectedVersion) {
        if (expectedVersion < 0) throw validation("expectedVersion", "debe ser mayor o igual a 0");
        if (context.execution().getState() != ExecutionState.IN_PROGRESS) {
            throw conflict("EXECUTION_ALREADY_SUBMITTED", "La ejecución ya fue enviada y no puede modificarse");
        }
        ensureWindow(context, clock.instant());
        if (context.execution().getVersion() != expectedVersion) {
            throw new ApiConflictException("VERSION_CONFLICT", "La ejecución cambió; vuelva a consultarla",
                    context.execution().getVersion());
        }
    }

    private void ensureWindow(Context context, Instant now) {
        if (context.obligation().isExpired(now)) {
            throw conflict("OBLIGATION_OVERDUE", "La obligación está vencida y no admite modificaciones");
        }
        if (context.obligation().getStartsAt() == null || now.isBefore(context.obligation().getStartsAt())) {
            throw conflict("EXECUTION_NOT_STARTED_YET", "La ventana de ejecución todavía no comenzó");
        }
    }

    private void validateMaximum(Context context) {
        long count = evidence.countByExecutionIdAndEvidenceRequirementId(
                context.execution().getId(), context.requirement().getId());
        if (count >= context.requirement().getMaximumCount()) {
            throw conflict("EVIDENCE_LIMIT_REACHED", "El requisito ya alcanzó su cantidad máxima de evidencias");
        }
    }

    private Optional<Evidence> replay(User actor, String key, String requestHash) {
        idempotency.deleteByExpiresAtBefore(clock.instant());
        return idempotency.findByCompanyIdAndActorIdAndCommandAndIdempotencyKey(
                        actor.getCompanyId(), actor.getId(), ATTACH_EVIDENCE, key)
                .map(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        throw conflict("IDEMPOTENCY_KEY_MISMATCH", "La clave ya fue usada con otra solicitud");
                    }
                    return evidence.findById(record.getEvidenceId())
                            .orElseThrow(() -> new IllegalStateException("Idempotency record without evidence"));
                });
    }

    private void record(User actor, String key, String requestHash, Long evidenceId, Instant now) {
        idempotency.saveAndFlush(new EvidenceIdempotency(actor.getCompanyId(), actor.getId(), ATTACH_EVIDENCE,
                key, requestHash, evidenceId, now, now.plus(IDEMPOTENCY_RETENTION)));
    }

    private void lockActor(User actor) {
        if (actor == null) throw new AccessDeniedException("Actor unavailable");
        users.lockById(actor.getId()).orElseThrow(() -> new AccessDeniedException("Actor unavailable"));
    }

    private void compensateStorageOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException cleanupFailure) {
                        LOG.warn("A private evidence upload requires cleanup");
                    }
                } else if (status == STATUS_UNKNOWN) {
                    LOG.warn("A private evidence transaction requires reconciliation");
                }
            }
        });
    }

    static EvidenceView view(Evidence value) {
        String contentPath = value.getStorageKey() == null ? null : "/api/v1/evidence/" + value.getId() + "/content";
        return new EvidenceView(value.getId(), value.getExecutionId(), value.getEvidenceRequirementId(),
                value.getType(), value.getCreatedBy(), value.getTextValue(), value.getLatitude(),
                value.getLongitude(), value.getAccuracyMeters(), value.getContentType(), value.getFileSize(),
                value.getChecksum(), value.getCapturedAt(), value.getCreatedAt(), contentPath);
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw validation("idempotencyKey",
                    "debe contener entre 8 y 100 caracteres alfanuméricos o . _ : -");
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static FieldValidationException validation(String field, String message) {
        return new FieldValidationException("Evidencia inválida", Map.of(field, List.of(message)));
    }

    private static ApiConflictException conflict(String code, String message) {
        return new ApiConflictException(code, message);
    }

    private record Context(Obligation obligation, Execution execution,
                           ObligationEvidenceRequirement requirement) {}

    public record StructuredEvidenceCommand(
            @Size(max = 512) String textValue,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyMeters,
            @NotNull Instant capturedAt,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record EvidenceView(Long id, Long executionId, Long evidenceRequirementId, EvidenceType type,
                               Long createdBy, String textValue, BigDecimal latitude, BigDecimal longitude,
                               BigDecimal accuracyMeters, String contentType, Long fileSize, String checksum,
                               Instant capturedAt, Instant createdAt, String contentPath) {}
}
