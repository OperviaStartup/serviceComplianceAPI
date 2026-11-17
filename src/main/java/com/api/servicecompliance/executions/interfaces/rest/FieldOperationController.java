package com.api.servicecompliance.executions.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.executions.application.FieldExecutionService;
import com.api.servicecompliance.executions.application.RequiredEvidenceService;
import com.api.servicecompliance.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.time.Instant;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1")
@Tag(name="Operator execution flow", description="Ejecución, avance, fotos privadas e impedimentos")
@SecurityRequirement(name="bearerAuth")
@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
public class FieldOperationController {
    private final FieldExecutionService executions;
    private final RequiredEvidenceService requiredEvidence;
    private final AuthService auth;
    public FieldOperationController(FieldExecutionService executions, RequiredEvidenceService requiredEvidence,
                                    AuthService auth) {
        this.executions=executions; this.requiredEvidence=requiredEvidence; this.auth=auth;
    }

    @PostMapping("/obligations/{id}/execution")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary="Iniciar la única ejecución de una obligación", description="Requiere Idempotency-Key. Devuelve 201 al iniciar y 200 para el replay exacto. La ventana es [startsAt,dueAt).")
    @ApiResponse(responseCode="201", description="Ejecución iniciada")
    @ApiResponse(responseCode="200", description="Replay idempotente de la ejecución")
    @ApiResponse(responseCode="400", description="Clave idempotente inválida", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="403", description="El operador no tiene asignada la obligación", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="404", description="Obligación fuera del alcance", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="409", description="Estado, ventana o replay incompatible", content=@Content(schema=@Schema(implementation=ApiError.class)))
    public ResponseEntity<FieldExecutionService.ExecutionView> start(
            @PathVariable Long id,
            @Parameter(required=true, description="Clave de 8 a 100 caracteres para iniciar esta ejecución")
            @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey,
            Principal principal) {
        var result=executions.start(id, idempotencyKey, auth.currentUser(principal.getName()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.execution());
    }

    @GetMapping("/obligations/{id}/execution")
    @Operation(summary="Recuperar la ejecución y su avance")
    public FieldExecutionService.ExecutionView current(@PathVariable Long id, Principal principal) {
        return executions.current(id, auth.currentUser(principal.getName()));
    }

    @GetMapping("/executions/{id}/flow")
    @Operation(summary="Consultar el flujo completo de una ejecución")
    public FieldExecutionService.ExecutionView find(@PathVariable Long id, Principal principal) {
        return executions.find(id, auth.currentUser(principal.getName()));
    }

    @PutMapping("/executions/{id}/steps/{stepId}")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary="Marcar o desmarcar un paso", description="Requiere expectedVersion. No modifica otras instrucciones ni el listado.")
    public FieldExecutionService.ExecutionView step(@PathVariable Long id, @PathVariable Long stepId,
            @Valid @RequestBody FieldExecutionService.StepCommand command, Principal principal) {
        return executions.updateStep(id, stepId, command, auth.currentUser(principal.getName()));
    }

    @PostMapping("/executions/{id}/exceptions")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary="Registrar un impedimento sin completar ni evaluar la obligación", description="operationId identifica el reintento; stepId es opcional. No elimina pasos ni fotos pendientes.")
    public FieldExecutionService.ExecutionView exception(@PathVariable Long id,
            @Valid @RequestBody FieldExecutionService.ExceptionCommand command, Principal principal) {
        return executions.reportException(id, command, auth.currentUser(principal.getName()));
    }

    @PostMapping(value="/executions/{id}/photo-requirements/{requirementId}/photo", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary="Subir la fotografía de un requisito", description="JPEG/PNG hasta 5 MB, expectedVersion obligatorio. Una fotografía inmutable por requisito; repetir los mismos bytes devuelve el registro original. No acepta URLs.")
    public FieldExecutionService.ExecutionView photo(@PathVariable Long id, @PathVariable Long requirementId,
            @RequestPart("file") MultipartFile file, @RequestParam long expectedVersion,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant capturedAt,
            Principal principal) {
        return executions.attachPhoto(id, requirementId, file, expectedVersion, capturedAt,
                auth.currentUser(principal.getName()));
    }

    @PostMapping(value="/executions/{id}/evidence-requirements/{requirementId}/evidence",
            consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary="Adjuntar evidencia fotográfica o firma privada",
            description="Requiere Idempotency-Key. PHOTO acepta JPEG/PNG hasta 5 MB y SIGNATURE solo PNG hasta 1 MB. La evidencia es inmutable.")
    @ApiResponse(responseCode="200", description="Evidencia registrada o replay idempotente")
    @ApiResponse(responseCode="400", description="Archivo, captura o geolocalización inválidos",
            content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="403", description="El operador no es propietario",
            content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="404", description="Ejecución o requisito fuera del alcance",
            content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="409", description="Versión, límite o clave incompatible",
            content=@Content(schema=@Schema(implementation=ApiError.class)))
    public RequiredEvidenceService.EvidenceView binaryEvidence(
            @PathVariable Long id, @PathVariable Long requirementId,
            @Parameter(required=true, description="Clave idempotente conservada por 7 días")
            @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey,
            @RequestPart("file") MultipartFile file,
            @RequestParam long expectedVersion,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant capturedAt,
            @RequestParam(required=false) BigDecimal latitude,
            @RequestParam(required=false) BigDecimal longitude,
            @RequestParam(required=false) BigDecimal accuracyMeters,
            Principal principal) {
        return requiredEvidence.attachBinary(id, requirementId, idempotencyKey, file, expectedVersion,
                capturedAt, latitude, longitude, accuracyMeters, auth.currentUser(principal.getName()));
    }

    @PostMapping(value="/executions/{id}/evidence-requirements/{requirementId}/evidence",
            consumes=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary="Registrar evidencia QR o ubicación",
            description="Requiere Idempotency-Key. QR_CODE admite hasta 512 caracteres y LOCATION valida coordenadas y precisión. La evidencia es inmutable.")
    @ApiResponse(responseCode="200", description="Evidencia registrada o replay idempotente")
    @ApiResponse(responseCode="400", description="Valor QR, captura o ubicación inválidos",
            content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="403", description="El operador no es propietario",
            content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="404", description="Ejecución o requisito fuera del alcance",
            content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="409", description="Versión, límite o clave incompatible",
            content=@Content(schema=@Schema(implementation=ApiError.class)))
    public RequiredEvidenceService.EvidenceView structuredEvidence(
            @PathVariable Long id, @PathVariable Long requirementId,
            @Parameter(required=true, description="Clave idempotente conservada por 7 días")
            @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey,
            @Valid @RequestBody RequiredEvidenceService.StructuredEvidenceCommand command,
            Principal principal) {
        return requiredEvidence.attachStructured(id, requirementId, idempotencyKey, command,
                auth.currentUser(principal.getName()));
    }

    @PostMapping("/executions/{id}/submit")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary="Enviar la ejecución completa", description="Requiere Idempotency-Key. Valida todos los pasos, fotos, fecha límite y expectedVersion. El servidor determina COMPLETED y SUBMITTED; no registra una evaluación de cumplimiento.")
    @ApiResponse(responseCode="200", description="Ejecución enviada o replay exacto")
    @ApiResponse(responseCode="400", description="Solicitud incompleta o clave inválida", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="403", description="El operador no es propietario", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="404", description="Ejecución fuera del alcance", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="409", description="Versión, estado o replay incompatible", content=@Content(schema=@Schema(implementation=ApiError.class)))
    public FieldExecutionService.ExecutionView submit(
            @PathVariable Long id,
            @Parameter(required=true, description="Clave de 8 a 100 caracteres para enviar esta ejecución")
            @RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey,
            @Valid @RequestBody FieldExecutionService.SubmitCommand command, Principal principal) {
        return executions.submit(id, idempotencyKey, command, auth.currentUser(principal.getName()));
    }

    @GetMapping("/evidence/{id}/content")
    @Operation(summary="Consultar una fotografía privada con autorización")
    @ApiResponse(responseCode="200", content={
            @Content(mediaType="image/jpeg", schema=@Schema(type="string", format="binary")),
            @Content(mediaType="image/png", schema=@Schema(type="string", format="binary"))})
    public ResponseEntity<byte[]> content(@PathVariable Long id, Principal principal) {
        var photo=executions.photo(id, auth.currentUser(principal.getName()));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=photo-"+id
                        +(photo.contentType().equals("image/png") ? ".png" : ".jpg"))
                .body(photo.data());
    }
}
