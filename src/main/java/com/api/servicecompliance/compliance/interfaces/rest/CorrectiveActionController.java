package com.api.servicecompliance.compliance.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.compliance.application.CorrectiveAttentionService;
import com.api.servicecompliance.compliance.application.CorrectiveActionService;
import com.api.servicecompliance.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Corrective actions", description = "Asignación y consulta de trabajo correctivo")
@SecurityRequirement(name = "bearerAuth")
public class CorrectiveActionController {
    private final CorrectiveActionService actions;
    private final CorrectiveAttentionService attention;
    private final AuthService auth;

    public CorrectiveActionController(CorrectiveActionService actions,
                                      CorrectiveAttentionService attention, AuthService auth) {
        this.actions = actions;
        this.attention = attention;
        this.auth = auth;
    }

    @PostMapping("/compliance-cases/{caseId}/corrective-actions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Asignar una acción correctiva",
            description = "Crea trabajo correctivo para el último resultado NON_COMPLIANT del caso.")
    @ApiResponse(responseCode = "201", description = "Acción correctiva asignada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Supervisor u operario asignado fuera de alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Caso inexistente o fuera del alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "El último resultado no es NON_COMPLIANT",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CorrectiveActionService.CorrectiveActionResponse create(
            @PathVariable Long caseId,
            @Valid @RequestBody CorrectiveActionService.CreateCorrectiveActionCommand command,
            Principal principal) {
        return actions.create(caseId, command, auth.currentUser(principal.getName()));
    }

    @GetMapping("/corrective-actions/{actionId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','OPERATOR')")
    @Operation(summary = "Consultar una acción correctiva",
            description = "Devuelve estado persistido, estado operativo derivado, versión e historial.")
    @ApiResponse(responseCode = "200", description = "Acción correctiva autorizada")
    @ApiResponse(responseCode = "403", description = "El operario no es el asignado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Acción inexistente o fuera del alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public CorrectiveActionService.CorrectiveActionResponse get(
            @PathVariable Long actionId, Principal principal) {
        return actions.get(actionId, auth.currentUser(principal.getName()));
    }

    @PostMapping(value = "/corrective-actions/{actionId}/attention-submissions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Enviar la atención de una acción correctiva",
            description = "Solicitud multipart atómica con command JSON, 1 a 5 archivos JPEG/PNG e Idempotency-Key.")
    @ApiResponse(responseCode = "201", description = "Atención enviada")
    @ApiResponse(responseCode = "200", description = "Replay idempotente del envío original")
    @ApiResponse(responseCode = "400", description = "Clave, detalles o fotografías inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "El operador no es el asignado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Acción fuera del alcance",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Versión, estado o replay incompatible",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<CorrectiveAttentionService.AttentionSubmissionResponse> submitAttention(
            @PathVariable Long actionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestPart("command") CorrectiveAttentionService.SubmissionCommand command,
            @RequestPart("files") List<MultipartFile> files,
            Principal principal) {
        var result = attention.submit(actionId, idempotencyKey, command, files,
                auth.currentUser(principal.getName()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.submission());
    }

    @GetMapping("/corrective-actions/{actionId}/attention-submissions")
    @PreAuthorize("hasAnyRole('SUPERVISOR','OPERATOR')")
    @Operation(summary = "Consultar los envíos correctivos inmutables")
    public List<CorrectiveAttentionService.AttentionSubmissionResponse> submissions(
            @PathVariable Long actionId, Principal principal) {
        return attention.list(actionId, auth.currentUser(principal.getName()));
    }

    @PostMapping("/corrective-actions/{actionId}/return")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Devolver la atención al operador")
    public CorrectiveActionService.CorrectiveActionResponse returnAttention(
            @PathVariable Long actionId,
            @Valid @RequestBody CorrectiveActionService.TransitionCommand command,
            Principal principal) {
        return actions.returnForCorrection(actionId, command, auth.currentUser(principal.getName()));
    }

    @PostMapping("/corrective-actions/{actionId}/verify")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Verificar y cerrar una acción correctiva")
    public CorrectiveActionService.CorrectiveActionResponse verifyAttention(
            @PathVariable Long actionId,
            @Valid @RequestBody CorrectiveActionService.TransitionCommand command,
            Principal principal) {
        return actions.verifyAndClose(actionId, command, auth.currentUser(principal.getName()));
    }

    @GetMapping("/corrective-actions/{actionId}/evidence/{evidenceId}/content")
    @PreAuthorize("hasAnyRole('SUPERVISOR','OPERATOR')")
    @Operation(summary = "Descargar una evidencia correctiva privada")
    public ResponseEntity<byte[]> evidenceContent(@PathVariable Long actionId,
                                                   @PathVariable Long evidenceId,
                                                   Principal principal) {
        var content = attention.content(actionId, evidenceId, auth.currentUser(principal.getName()));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=corrective-evidence-"
                        + evidenceId + (content.contentType().equals("image/png") ? ".png" : ".jpg"))
                .body(content.data());
    }
}
