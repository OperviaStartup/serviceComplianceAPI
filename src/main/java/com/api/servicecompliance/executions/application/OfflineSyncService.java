package com.api.servicecompliance.executions.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.executions.domain.model.*;
import com.api.servicecompliance.executions.domain.repository.OfflineSyncOperationRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OfflineSyncService {
    private static final Logger LOG=LoggerFactory.getLogger(OfflineSyncService.class);
    private static final Duration RETENTION=Duration.ofDays(30);
    private static final long MAX_BATCH_BYTES=25L*1024*1024;
    private final FieldExecutionService executions;
    private final RequiredEvidenceService evidence;
    private final OfflineSyncOperationRepository operations;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public OfflineSyncService(FieldExecutionService executions, RequiredEvidenceService evidence,
                              OfflineSyncOperationRepository operations,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.executions=executions; this.evidence=evidence; this.operations=operations; this.clock=clock;
        this.transactions=new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public SyncBatchResponse synchronize(SyncBatchRequest request, Map<String,MultipartFile> files, User actor) {
        validateEnvelope(request,files,actor);
        transactions.executeWithoutResult(status -> operations.deleteByExpiresAtBefore(clock.instant()));
        List<SyncItem> ordered=topological(request.items());
        Map<UUID,SyncItemResult> completed=new HashMap<>();
        for (SyncItem item:ordered) completed.put(item.clientOperationId(),process(item,files,actor,completed));
        return new SyncBatchResponse(request.batchId(),request.items().stream()
                .map(item -> completed.get(item.clientOperationId())).toList());
    }

    private SyncItemResult process(SyncItem item, Map<String,MultipartFile> files, User actor,
                                   Map<UUID,SyncItemResult> completed) {
        String requestHash=hash(canonical(item,files.get(item.fileKey())));
        try {
            return transactions.execute(status -> {
                Optional<OfflineSyncOperation> previous=find(actor,item.clientOperationId());
                if (previous.isPresent()) return replay(previous.get(),item,requestHash);
                SyncItemResult dependencyFailure=dependencyFailure(item,actor,completed);
                if (dependencyFailure!=null) {
                    if (dependencyFailure.outcome()==SyncOutcome.REQUIRES_ATTENTION)
                        save(actor,item,requestHash,dependencyFailure);
                    return dependencyFailure;
                }
                SyncItemResult result=apply(item,files,actor);
                save(actor,item,requestHash,result);
                return result;
            });
        } catch (ApiConflictException exception) {
            return attention(actor,item,requestHash,exception.getCode(),exception.getCurrentVersion());
        } catch (FieldValidationException exception) {
            return attention(actor,item,requestHash,"VALIDATION_ERROR",null);
        } catch (ResourceNotFoundException exception) {
            return attention(actor,item,requestHash,"RESOURCE_NOT_FOUND",null);
        } catch (AccessDeniedException exception) {
            return attention(actor,item,requestHash,"FORBIDDEN",null);
        } catch (DataIntegrityViolationException exception) {
            return transactions.execute(status -> find(actor,item.clientOperationId())
                    .map(record -> replay(record,item,requestHash))
                    .orElseGet(() -> retryable(item,"TEMPORARY_CONFLICT")));
        } catch (PhotoStorageUnavailableException | TransientDataAccessException exception) {
            return retryable(item,"TEMPORARY_UNAVAILABLE");
        } catch (RuntimeException exception) {
            LOG.error("Offline synchronization operation failed",exception);
            return retryable(item,"TEMPORARY_FAILURE");
        }
    }

    private SyncItemResult apply(SyncItem item, Map<String,MultipartFile> files, User actor) {
        String key="offline-"+item.clientOperationId();
        return switch(item.type()) {
            case START_EXECUTION -> {
                Long obligationId=required(item.obligationId(),"obligationId");
                var value=executions.start(obligationId,key,actor).execution();
                yield synced(item,"EXECUTION",value.id(),value.version());
            }
            case UPDATE_STEP -> {
                Long executionId=executionId(item,actor);
                var value=executions.updateStep(executionId,required(item.stepId(),"stepId"),
                        new FieldExecutionService.StepCommand(required(item.completed(),"completed"),
                                required(item.expectedVersion(),"expectedVersion")),actor);
                yield synced(item,"EXECUTION",value.id(),value.version());
            }
            case ATTACH_EVIDENCE -> {
                Long executionId=executionId(item,actor);
                Long requirementId=required(item.evidenceRequirementId(),"evidenceRequirementId");
                Long expectedVersion=required(item.expectedVersion(),"expectedVersion");
                RequiredEvidenceService.EvidenceView value;
                if (item.fileKey()!=null) {
                    MultipartFile file=files.get(item.fileKey());
                    if (file==null) throw validation("fileKey","no corresponde a un archivo del batch");
                    value=evidence.attachBinary(executionId,requirementId,key,file,expectedVersion,
                            required(item.capturedAt(),"capturedAt"),item.latitude(),item.longitude(),
                            item.accuracyMeters(),actor);
                } else {
                    value=evidence.attachStructured(executionId,requirementId,key,
                            new RequiredEvidenceService.StructuredEvidenceCommand(item.textValue(),
                                    item.latitude(),item.longitude(),item.accuracyMeters(),
                                    required(item.capturedAt(),"capturedAt"),expectedVersion),actor);
                }
                long version=executions.find(executionId,actor).version();
                yield synced(item,"EVIDENCE",value.id(),version);
            }
            case REPORT_EXCEPTION -> {
                Long executionId=executionId(item,actor);
                List<Long> evidenceIds=new ArrayList<>(item.evidenceIds()==null?List.of():item.evidenceIds());
                for(UUID reference:item.evidenceRefs()==null?List.<UUID>of():item.evidenceRefs())
                    evidenceIds.add(resource(actor,reference,"EVIDENCE"));
                var value=executions.reportException(executionId,key,
                        new FieldExecutionService.ExceptionCommand(required(item.reason(),"reason"),
                                requiredText(item.description(),"description"),item.stepId(),
                                item.clientOperationId(),evidenceIds,
                                required(item.expectedVersion(),"expectedVersion")),actor);
                Long exceptionId=value.exceptions().stream()
                        .filter(candidate -> candidate.operationId().equals(item.clientOperationId()))
                        .map(FieldExecutionService.ExceptionView::id).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Exception result unavailable"));
                yield synced(item,"EXCEPTION",exceptionId,value.version());
            }
            case SUBMIT_EXECUTION -> {
                Long executionId=executionId(item,actor);
                var value=executions.submit(executionId,key,
                        new FieldExecutionService.SubmitCommand(
                                required(item.expectedVersion(),"expectedVersion"),item.notes()),actor);
                yield synced(item,"EXECUTION",value.id(),value.version());
            }
        };
    }

    private Long executionId(SyncItem item, User actor) {
        if (item.executionId()!=null && item.executionRef()!=null)
            throw validation("execution","use executionId o executionRef, no ambos");
        if (item.executionId()!=null) return item.executionId();
        if (item.executionRef()!=null) return resource(actor,item.executionRef(),"EXECUTION");
        throw validation("execution","executionId o executionRef es obligatorio");
    }

    private Long resource(User actor, UUID reference, String expectedType) {
        OfflineSyncOperation record=find(actor,reference)
                .orElseThrow(() -> validation("clientRef","la operación referenciada no está sincronizada"));
        if (record.getOutcome()!=SyncOutcome.SYNCED || !expectedType.equals(record.getResourceType()))
            throw validation("clientRef","la operación referenciada no produjo "+expectedType);
        return record.getResourceId();
    }

    private SyncItemResult dependencyFailure(SyncItem item, User actor,
                                             Map<UUID,SyncItemResult> completed) {
        for(UUID dependency:dependencies(item)) {
            SyncItemResult result=completed.get(dependency);
            if (result==null) result=find(actor,dependency).map(this::response).orElse(null);
            if (result==null) return attentionValue(item,"DEPENDENCY_NOT_FOUND",null);
            if (result.outcome()==SyncOutcome.RETRYABLE)
                return retryable(item,"DEPENDENCY_RETRYABLE");
            if (result.outcome()==SyncOutcome.REQUIRES_ATTENTION)
                return attentionValue(item,"DEPENDENCY_REQUIRES_ATTENTION",result.currentVersion());
        }
        return null;
    }

    private SyncItemResult attention(User actor, SyncItem item, String hash, String code, Long version) {
        try {
            return transactions.execute(status -> {
                Optional<OfflineSyncOperation> existing=find(actor,item.clientOperationId());
                if(existing.isPresent()) return replay(existing.get(),item,hash);
                SyncItemResult result=attentionValue(item,code,version);
                save(actor,item,hash,result); return result;
            });
        } catch (DataIntegrityViolationException exception) {
            return transactions.execute(status -> find(actor,item.clientOperationId())
                    .map(record -> replay(record,item,hash))
                    .orElseGet(() -> retryable(item,"TEMPORARY_CONFLICT")));
        }
    }

    private SyncItemResult replay(OfflineSyncOperation record, SyncItem item, String hash) {
        if (!record.getRequestHash().equals(hash))
            return attentionValue(item,"IDEMPOTENCY_KEY_MISMATCH",record.getCurrentVersion());
        return response(record);
    }

    private SyncItemResult response(OfflineSyncOperation value) {
        return new SyncItemResult(value.getClientOperationId(),value.getOutcome(),value.getResultCode(),
                value.getResourceType(),value.getResourceId(),value.getCurrentVersion());
    }

    private void save(User actor, SyncItem item, String hash, SyncItemResult result) {
        Instant now=clock.instant();
        operations.saveAndFlush(new OfflineSyncOperation(actor.getCompanyId(),actor.getId(),
                item.clientOperationId(),item.type(),hash,result.outcome(),result.code(),
                result.resourceType(),result.resourceId(),result.currentVersion(),now,now.plus(RETENTION)));
    }

    private Optional<OfflineSyncOperation> find(User actor, UUID id) {
        return operations.findByCompanyIdAndActorIdAndClientOperationId(actor.getCompanyId(),actor.getId(),id);
    }

    private void validateEnvelope(SyncBatchRequest request, Map<String,MultipartFile> files, User actor) {
        if(actor==null || actor.getRole()!=com.api.servicecompliance.shared.domain.Role.OPERATOR || !actor.isActive())
            throw new AccessDeniedException("Operator role required");
        if(request==null || request.items()==null || request.items().isEmpty() || request.items().size()>50)
            throw validation("items","debe contener entre 1 y 50 operaciones");
        if(files.size()>10 || files.values().stream().mapToLong(MultipartFile::getSize).sum()>MAX_BATCH_BYTES)
            throw validation("files","admite hasta 10 archivos y 25 MiB por batch");
        Set<String> referenced=request.items().stream().map(SyncItem::fileKey).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if(!files.keySet().equals(referenced))
            throw validation("files","cada archivo debe tener una única referencia fileKey");
        Set<UUID> ids=new HashSet<>();
        if(request.items().stream().anyMatch(item -> item.clientOperationId()==null || item.type()==null
                || !ids.add(item.clientOperationId())))
            throw validation("items","clientOperationId y type son obligatorios y no pueden repetirse");
    }

    private List<SyncItem> topological(List<SyncItem> items) {
        Map<UUID,SyncItem> byId=items.stream().collect(Collectors.toMap(SyncItem::clientOperationId,i->i));
        List<SyncItem> ordered=new ArrayList<>(); Set<UUID> visiting=new HashSet<>(),visited=new HashSet<>();
        for(SyncItem item:items) visit(item,byId,visiting,visited,ordered);
        return ordered;
    }

    private void visit(SyncItem item, Map<UUID,SyncItem> byId, Set<UUID> visiting,
                       Set<UUID> visited, List<SyncItem> ordered) {
        if(visited.contains(item.clientOperationId())) return;
        if(!visiting.add(item.clientOperationId())) throw validation("dependsOn","contiene un ciclo");
        for(UUID dependency:dependencies(item)) if(byId.containsKey(dependency))
            visit(byId.get(dependency),byId,visiting,visited,ordered);
        visiting.remove(item.clientOperationId()); visited.add(item.clientOperationId()); ordered.add(item);
    }

    private Set<UUID> dependencies(SyncItem item) {
        Set<UUID> result=new LinkedHashSet<>(item.dependsOn()==null?List.of():item.dependsOn());
        if(item.executionRef()!=null) result.add(item.executionRef());
        if(item.evidenceRefs()!=null) result.addAll(item.evidenceRefs());
        return result;
    }

    private String canonical(SyncItem item, MultipartFile file) {
        List<String> deps=dependencies(item).stream().map(UUID::toString).sorted().toList();
        List<Long> evidenceIds=(item.evidenceIds()==null?List.<Long>of():item.evidenceIds()).stream().sorted().toList();
        return String.join("|",item.type().name(),String.valueOf(item.obligationId()),
                String.valueOf(item.executionId()),String.valueOf(item.executionRef()),
                String.valueOf(item.stepId()),String.valueOf(item.completed()),
                String.valueOf(item.evidenceRequirementId()),String.valueOf(item.fileKey()),
                String.valueOf(item.textValue()),decimal(item.latitude()),decimal(item.longitude()),
                decimal(item.accuracyMeters()),String.valueOf(item.capturedAt()),
                String.valueOf(item.reason()),normalize(item.description()),evidenceIds.toString(),
                deps.toString(),String.valueOf(item.expectedVersion()),normalize(item.notes()),fileHash(file));
    }

    private String fileHash(MultipartFile file) {
        if(file==null) return "";
        try { return hash(file.getBytes()); }
        catch(IOException exception) { throw validation("files","no se pudo leer el archivo"); }
    }

    private static String normalize(String value) { return value==null?"":value.trim(); }
    private static String decimal(BigDecimal value) {
        return value==null?"null":value.stripTrailingZeros().toPlainString();
    }
    private static String requiredText(String value,String field) {
        if(value==null || value.isBlank()) throw validation(field,"es obligatorio");
        return value;
    }
    private static <T> T required(T value,String field) {
        if(value==null) throw validation(field,"es obligatorio"); return value;
    }
    private static FieldValidationException validation(String field,String message) {
        return new FieldValidationException("Batch de sincronización inválido",Map.of(field,List.of(message)));
    }
    private static SyncItemResult synced(SyncItem item,String type,Long id,Long version) {
        return new SyncItemResult(item.clientOperationId(),SyncOutcome.SYNCED,"SYNCED",type,id,version);
    }
    private static SyncItemResult retryable(SyncItem item,String code) {
        return new SyncItemResult(item.clientOperationId(),SyncOutcome.RETRYABLE,code,null,null,null);
    }
    private static SyncItemResult attentionValue(SyncItem item,String code,Long version) {
        return new SyncItemResult(item.clientOperationId(),SyncOutcome.REQUIRES_ATTENTION,code,null,null,version);
    }
    private static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record SyncBatchRequest(@NotNull UUID batchId,
                                   @NotEmpty @Size(max=50) List<@Valid SyncItem> items) {}
    public record SyncItem(@NotNull UUID clientOperationId, @NotNull SyncOperationType type,
                           List<UUID> dependsOn, Long obligationId, Long executionId, UUID executionRef,
                           @Positive Long stepId, Boolean completed, @Positive Long evidenceRequirementId,
                           @Size(max=255) String fileKey, @Size(max=512) String textValue,
                           BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                           Instant capturedAt, ExceptionReason reason, @Size(max=2000) String description,
                           List<@Positive Long> evidenceIds, List<UUID> evidenceRefs,
                           @PositiveOrZero Long expectedVersion, @Size(max=2000) String notes) {}
    public record SyncItemResult(UUID clientOperationId, SyncOutcome outcome, String code,
                                 String resourceType, Long resourceId, Long currentVersion) {}
    public record SyncBatchResponse(UUID batchId,List<SyncItemResult> items) {}
}
