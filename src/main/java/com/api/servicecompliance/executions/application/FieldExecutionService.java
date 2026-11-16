package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.executions.domain.model.*;
import com.api.servicecompliance.executions.domain.repository.*;
import com.api.servicecompliance.obligations.domain.model.*;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.*;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@Transactional
public class FieldExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(FieldExecutionService.class);
    private static final String START_EXECUTION = "START_EXECUTION";
    private static final String SUBMIT_EXECUTION = "SUBMIT_EXECUTION";
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(7);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");
    private final ObligationRepository obligations;
    private final ExecutionRepository executions;
    private final ExecutionIdempotencyRepository idempotency;
    private final UserRepository users;
    private final ExecutionStepProgressRepository progress;
    private final EvidenceRepository evidence;
    private final OperationalExceptionRepository exceptions;
    private final PhotoStorage storage;
    private final PhotoValidator photos;
    private final Clock clock;

    public FieldExecutionService(ObligationRepository obligations, ExecutionRepository executions,
                                 ExecutionIdempotencyRepository idempotency,
                                 UserRepository users,
                                 ExecutionStepProgressRepository progress, EvidenceRepository evidence,
                                 OperationalExceptionRepository exceptions, PhotoStorage storage,
                                 PhotoValidator photos, Clock clock) {
        this.obligations=obligations; this.executions=executions; this.idempotency=idempotency; this.users=users;
        this.progress=progress;
        this.evidence=evidence; this.exceptions=exceptions; this.storage=storage; this.photos=photos; this.clock=clock;
    }

    public StartResponse start(Long obligationId, String idempotencyKey, User actor) {
        lockActor(actor);
        Obligation obligation = scopedObligation(obligationId, actor, true);
        validateIdempotencyKey(idempotencyKey);
        Instant now=clock.instant();
        idempotency.deleteByExpiresAtBefore(now);
        String requestHash=hash(String.valueOf(obligationId));
        Optional<Execution> replay=replay(actor, START_EXECUTION, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            Execution execution=replay.get();
            if (!execution.getObligationId().equals(obligationId)) {
                throw conflict("IDEMPOTENCY_KEY_MISMATCH", "La clave ya fue usada con otra solicitud");
            }
            checkOwner(execution, actor);
            return new StartResponse(view(obligation, execution), false);
        }
        List<Execution> existing=executions.findByObligationId(obligationId);
        if (!existing.isEmpty()) {
            Execution execution=single(existing);
            checkOwner(execution, actor);
            if (execution.getState()!=ExecutionState.IN_PROGRESS
                    || idempotency.existsByExecutionIdAndCommand(execution.getId(), START_EXECUTION)) {
                throw conflict("EXECUTION_ALREADY_STARTED", "La obligación ya tiene una ejecución iniciada");
            }
            record(actor, START_EXECUTION, idempotencyKey, requestHash, execution.getId(), now);
            return new StartResponse(view(obligation, execution), false);
        }
        if (obligation.getStartsAt()==null || obligation.getSteps().isEmpty()) {
            throw conflict("EXECUTION_NOT_CONFIGURED", "La obligación no tiene configurado su detalle de ejecución");
        }
        window(obligation, now);
        if (obligation.getStatus()!=ObligationStatus.ASSIGNED) {
            throw conflict("INVALID_EXECUTION_STATE", "La obligación no puede iniciarse");
        }
        Execution execution=executions.saveAndFlush(Execution.start(obligationId, actor.getId(), now));
        progress.saveAll(obligation.getSteps().stream()
                .map(step -> new ExecutionStepProgress(execution.getId(), step.getId(), now)).toList());
        obligation.markInProgress();
        record(actor, START_EXECUTION, idempotencyKey, requestHash, execution.getId(), now);
        executions.flush();
        return new StartResponse(view(obligation, execution), true);
    }

    public ExecutionView current(Long obligationId, User actor) {
        Obligation obligation=scopedObligation(obligationId, actor, false);
        Execution execution=single(executions.findByObligationId(obligationId));
        checkOwner(execution, actor);
        return view(obligation, execution);
    }

    public ExecutionView find(Long executionId, User actor) {
        Context context=context(executionId, actor, false);
        return view(context.obligation(), context.execution());
    }

    public ExecutionView updateStep(Long executionId, Long stepId, StepCommand command, User actor) {
        Context context=context(executionId, actor, true);
        editable(context);
        var step=progress.findByExecutionId(executionId).stream().filter(p -> p.getStepId().equals(stepId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Paso no encontrado en esta ejecución"));
        if (step.isCompleted()!=command.completed()) {
            version(context.execution(), command.expectedVersion());
            Instant changedAt=clock.instant();
            editable(context, changedAt);
            step.update(command.completed(), changedAt);
            context.execution().stepChanged(command.completed(), changedAt);
            executions.flush();
        }
        return view(context.obligation(), context.execution());
    }

    public ExecutionView reportException(Long executionId, ExceptionCommand command, User actor) {
        Context context=context(executionId, actor, true);
        var previous=exceptions.findByExecutionIdAndOperationId(executionId, command.operationId());
        String description=command.description().trim();
        if (previous.isPresent()) {
            var stored=previous.get();
            if (stored.getReason()!=command.reason() || !Objects.equals(stored.getStepId(), command.stepId())
                    || !stored.getDescription().equals(description)) {
                throw conflict("IDEMPOTENCY_KEY_MISMATCH", "La operación ya fue usada con otro impedimento");
            }
            return view(context.obligation(), context.execution());
        }
        editable(context);
        version(context.execution(), command.expectedVersion());
        if (command.stepId()!=null && context.obligation().getSteps().stream()
                .noneMatch(step -> step.getId().equals(command.stepId()))) {
            throw new ResourceNotFoundException("Paso no encontrado en esta obligación");
        }
        Instant reportedAt=clock.instant();
        editable(context, reportedAt);
        exceptions.save(new OperationalException(executionId, command.stepId(), command.reason(), description,
                actor.getId(), reportedAt, command.operationId()));
        context.execution().exceptionReported(reportedAt);
        executions.flush();
        return view(context.obligation(), context.execution());
    }

    public ExecutionView attachPhoto(Long executionId, Long requirementId, MultipartFile file,
                                      long expectedVersion, Instant capturedAt, User actor) {
        Context context=context(executionId, actor, true);
        if (expectedVersion<0) throw new FieldValidationException("Versión inválida", Map.of("expectedVersion", List.of("debe ser mayor o igual a 0")));
        if (context.obligation().getPhotoRequirements().stream().noneMatch(p -> p.getId().equals(requirementId))) {
            throw new ResourceNotFoundException("Requisito fotográfico no encontrado en esta obligación");
        }
        var photo=photos.validate(file);
        var previous=evidence.findByExecutionIdAndPhotoRequirementId(executionId, requirementId);
        if (previous.isPresent()) {
            if (!Objects.equals(previous.get().getChecksum(), photo.checksum())) {
                throw conflict("PHOTO_ALREADY_ATTACHED", "Este requisito ya tiene una fotografía; no puede reemplazarse");
            }
            return view(context.obligation(), context.execution());
        }
        editable(context);
        version(context.execution(), expectedVersion);
        if (capturedAt!=null && capturedAt.isAfter(clock.instant())) {
            throw new FieldValidationException("Fecha de captura inválida", Map.of("capturedAt", List.of("no puede ser futura")));
        }
        String key="company/"+context.obligation().getCompanyId()+"/site/"+context.obligation().getSiteId()
                +"/execution/"+executionId+"/requirement/"+requirementId+"/"+UUID.randomUUID()+"."+photo.extension();
        // Compensate failed/uncertain uploads and DB rollbacks; never overwrite another object.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status==STATUS_ROLLED_BACK) {
                    try { storage.delete(key); }
                    catch (RuntimeException cleanupFailure) { LOG.warn("A private photo upload requires cleanup"); }
                } else if (status==STATUS_UNKNOWN) {
                    LOG.warn("A private photo transaction requires reconciliation");
                }
            }
        });
        storage.put(key, photo.data(), photo.contentType());
        // An upload that finishes beyond the deadline must not register a successful attachment.
        Instant uploadedAt=clock.instant();
        editable(context, uploadedAt);
        evidence.save(Evidence.photo(executionId, requirementId, key, photo.contentType(), photo.data().length,
                photo.checksum(), capturedAt, uploadedAt));
        context.execution().photoAttached(uploadedAt);
        executions.flush();
        return view(context.obligation(), context.execution());
    }

    public ExecutionView submit(Long executionId, String idempotencyKey, SubmitCommand command, User actor) {
        lockActor(actor);
        Context context=context(executionId, actor, true);
        validateIdempotencyKey(idempotencyKey);
        String notes=command.notes()==null || command.notes().isBlank() ? null : command.notes().trim();
        Instant now=clock.instant();
        idempotency.deleteByExpiresAtBefore(now);
        String requestHash=hash(executionId+"|"+(notes==null ? "" : notes));
        Optional<Execution> replay=replay(actor, SUBMIT_EXECUTION, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            if (!replay.get().getId().equals(executionId)) {
                throw conflict("IDEMPOTENCY_KEY_MISMATCH", "La clave ya fue usada con otra solicitud");
            }
            return view(context.obligation(), replay.get());
        }
        if (context.execution().getState()==ExecutionState.SUBMITTED) {
            if (!idempotency.existsByExecutionIdAndCommand(executionId, SUBMIT_EXECUTION)
                    && Objects.equals(notes, context.execution().getNotes())) {
                record(actor, SUBMIT_EXECUTION, idempotencyKey, requestHash, executionId, now);
                return view(context.obligation(), context.execution());
            }
            throw conflict("EXECUTION_ALREADY_SUBMITTED", "La ejecución ya fue enviada y no puede modificarse");
        }
        editable(context);
        version(context.execution(), command.expectedVersion());
        var done=progress.findByExecutionId(executionId).stream().filter(ExecutionStepProgress::isCompleted)
                .map(ExecutionStepProgress::getStepId).collect(Collectors.toSet());
        var attached=evidence.findByExecutionId(executionId).stream().map(Evidence::getPhotoRequirementId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var missing=new LinkedHashMap<String,List<String>>();
        if (!context.obligation().getSteps().stream().allMatch(step -> done.contains(step.getId()))) {
            missing.put("steps", List.of("deben estar marcados todos los pasos"));
        }
        if (!context.obligation().getPhotoRequirements().stream().allMatch(p -> attached.contains(p.getId()))) {
            missing.put("photoRequirements", List.of("deben estar registradas todas las fotografías exigidas"));
        }
        if (!missing.isEmpty()) throw new FieldValidationException("La ejecución está incompleta", missing);
        Instant submittedAt=clock.instant();
        editable(context, submittedAt);
        context.execution().submit(notes, submittedAt);
        context.obligation().markSubmitted();
        record(actor, SUBMIT_EXECUTION, idempotencyKey, requestHash, executionId, submittedAt);
        executions.flush();
        return view(context.obligation(), context.execution());
    }

    public PhotoContent photo(Long evidenceId, User actor) {
        Evidence photo=evidence.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Fotografía no encontrada"));
        context(photo.getExecutionId(), actor, false);
        if (photo.getStorageKey()==null) throw new ResourceNotFoundException("No existe un archivo gestionado para esta evidencia");
        byte[] data=storage.get(photo.getStorageKey());
        if (!Objects.equals(PhotoValidator.checksum(data), photo.getChecksum())) throw new PhotoStorageUnavailableException();
        return new PhotoContent(data, photo.getContentType());
    }

    private Obligation scopedObligation(Long id, User actor, boolean ownerOnly) {
        Obligation obligation=obligations.lockInScope(id, actor.getCompanyId(), actor.getSiteId())
                .orElseThrow(() -> new ResourceNotFoundException("Obligación no encontrada"));
        boolean owner=actor.getRole()==Role.OPERATOR
                && Objects.equals(obligation.getAssignedOperatorId(), actor.getId());
        if ((ownerOnly && !owner) || (actor.getRole()==Role.OPERATOR && !owner)) {
            throw new AccessDeniedException("Forbidden");
        }
        return obligation;
    }

    private Context context(Long executionId, User actor, boolean ownerOnly) {
        // Resolve the parent as a scalar first: do not cache a stale Execution before waiting for its lock.
        Long obligationId=executions.findObligationId(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Ejecución no encontrada"));
        Obligation obligation=scopedObligation(obligationId, actor, ownerOnly);
        Execution execution=executions.findById(executionId).orElseThrow();
        if (execution.getState()==ExecutionState.LEGACY) throw conflict("LEGACY_EXECUTION", "Esta ejecución pertenece al contrato anterior");
        if (actor.getRole()==Role.OPERATOR && !execution.getOperatorId().equals(actor.getId())) {
            throw new AccessDeniedException("Forbidden");
        }
        return new Context(obligation, execution);
    }

    private Execution single(List<Execution> matches) {
        if (matches.isEmpty()) throw new ResourceNotFoundException("La obligación aún no tiene ejecución");
        if (matches.size()!=1 || matches.get(0).getState()==ExecutionState.LEGACY) {
            throw conflict("LEGACY_EXECUTION", "La obligación tiene registros del contrato anterior; no se crea otra ejecución");
        }
        return matches.get(0);
    }

    private void window(Obligation obligation, Instant now) {
        if (obligation.isExpired(now)) throw conflict("OBLIGATION_OVERDUE", "La obligación está vencida y no admite modificaciones");
        if (obligation.getStartsAt()==null || now.isBefore(obligation.getStartsAt())) {
            throw conflict("EXECUTION_NOT_STARTED_YET", "La ventana de ejecución todavía no comenzó");
        }
    }
    private void editable(Context context) {
        editable(context, clock.instant());
    }
    private void editable(Context context, Instant now) {
        if (context.execution().getState()!=ExecutionState.IN_PROGRESS) {
            throw conflict("EXECUTION_ALREADY_SUBMITTED", "La ejecución ya fue enviada y no puede modificarse");
        }
        window(context.obligation(), now);
    }
    private void version(Execution execution, long expectedVersion) {
        if (expectedVersion<0) throw new FieldValidationException("Versión inválida", Map.of("expectedVersion", List.of("debe ser mayor o igual a 0")));
        if (execution.getVersion()!=expectedVersion) {
            throw new ApiConflictException("VERSION_CONFLICT", "La ejecución cambió; vuelva a consultarla", execution.getVersion());
        }
    }
    private ApiConflictException conflict(String code, String message) { return new ApiConflictException(code, message); }

    private void lockActor(User actor) {
        users.lockById(actor.getId()).orElseThrow(() -> new AccessDeniedException("Actor unavailable"));
    }

    private Optional<Execution> replay(User actor, String command, String key, String requestHash) {
        return idempotency.findByCompanyIdAndActorIdAndCommandAndIdempotencyKey(
                        actor.getCompanyId(), actor.getId(), command, key)
                .map(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        throw conflict("IDEMPOTENCY_KEY_MISMATCH", "La clave ya fue usada con otra solicitud");
                    }
                    return executions.findById(record.getExecutionId())
                            .orElseThrow(() -> new IllegalStateException("Idempotency record without execution"));
                });
    }

    private void record(User actor, String command, String key, String requestHash,
                        Long executionId, Instant now) {
        idempotency.saveAndFlush(new ExecutionIdempotency(actor.getCompanyId(), actor.getId(), command,
                key, requestHash, executionId, now, now.plus(IDEMPOTENCY_RETENTION)));
    }

    private static void validateIdempotencyKey(String key) {
        if (key==null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new FieldValidationException("Datos inválidos", Map.of("idempotencyKey", List.of(
                    "debe contener entre 8 y 100 caracteres alfanuméricos o . _ : -")));
        }
    }

    private static String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void checkOwner(Execution execution, User actor) {
        if (actor.getRole()==Role.OPERATOR && !execution.getOperatorId().equals(actor.getId())) {
            throw new AccessDeniedException("Forbidden");
        }
    }

    private ExecutionView view(Obligation obligation, Execution execution) {
        Map<Long,ExecutionStepProgress> states=progress.findByExecutionId(execution.getId()).stream()
                .collect(Collectors.toMap(ExecutionStepProgress::getStepId, Function.identity()));
        Map<Long,Evidence> attachments=evidence.findByExecutionId(execution.getId()).stream()
                .filter(e -> e.getPhotoRequirementId()!=null)
                .collect(Collectors.toMap(Evidence::getPhotoRequirementId, Function.identity()));
        List<StepView> steps=obligation.getSteps().stream().map(step -> {
            var state=states.get(step.getId());
            return new StepView(step.getId(), step.getTitle(), step.getInstructions(), step.getPosition(),
                    state!=null && state.isCompleted(), state==null ? null : state.getUpdatedAt());
        }).toList();
        List<PhotoView> required=obligation.getPhotoRequirements().stream().map(p -> {
            var photo=attachments.get(p.getId());
            return new PhotoView(p.getId(), p.getTitle(), p.getDescription(), p.getPosition(),
                    photo==null ? null : photo.getId(), photo==null ? null : photo.getCreatedAt(),
                    photo==null ? null : "/api/v1/evidence/"+photo.getId()+"/content");
        }).toList();
        boolean expired=obligation.isExpired(clock.instant());
        return new ExecutionView(execution.getId(), obligation.getId(), execution.getOperatorId(),
                execution.getState(), expired ? ObligationStatus.OVERDUE : obligation.getStatus(),
                execution.getResult(), execution.getStartedAt(), execution.getSubmittedAt(), obligation.getStartsAt(),
                obligation.getDueAt(), Obligation.TIMEZONE, execution.getVersion(),
                execution.getUpdatedAt(), execution.getState()==ExecutionState.IN_PROGRESS && !expired,
                execution.getNotes(), steps, required,
                exceptions.findByExecutionIdOrderByReportedAtAscIdAsc(execution.getId()).stream().map(e ->
                        new ExceptionView(e.getId(), e.getStepId(), e.getReason(), e.getDescription(),
                                e.getReportedBy(), e.getReportedAt(), e.getOperationId())).toList());
    }

    private record Context(Obligation obligation, Execution execution) {}
    public record StartResponse(ExecutionView execution, boolean created) {}
    public record StepCommand(@NotNull Boolean completed, @NotNull @PositiveOrZero Long expectedVersion) {}
    public record SubmitCommand(@NotNull @PositiveOrZero Long expectedVersion, @Size(max=2000) String notes) {}
    public record ExceptionCommand(@NotNull ExceptionReason reason, @NotBlank @Size(max=2000) String description,
                                   @Positive Long stepId, @NotNull UUID operationId,
                                   @NotNull @PositiveOrZero Long expectedVersion) {}
    public record StepView(Long id, String title, String instructions, int position, boolean completed, Instant updatedAt) {}
    public record PhotoView(Long id, String title, String description, int position, Long evidenceId,
                             Instant uploadedAt, String contentPath) {}
    public record ExceptionView(Long id, Long stepId, ExceptionReason reason, String description,
                                 Long reportedBy, Instant reportedAt, UUID operationId) {}
    public record ExecutionView(Long id, Long obligationId, Long operatorId, ExecutionState state,
                                 ObligationStatus obligationStatus, ExecutionResult result, Instant startedAt,
                                 Instant submittedAt, Instant startsAt, Instant dueAt, String timezone, long version,
                                 Instant updatedAt, boolean canEdit, String notes, List<StepView> steps,
                                 List<PhotoView> photoRequirements, List<ExceptionView> exceptions) {}
    public record PhotoContent(byte[] data, String contentType) {}
}
