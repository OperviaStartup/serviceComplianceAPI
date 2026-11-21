package com.api.servicecompliance.executions.interfaces.rest;

import com.api.servicecompliance.authentication.application.AuthService;
import com.api.servicecompliance.executions.application.OfflineSyncService;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.web.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/sync")
@Tag(name="Offline synchronization",description="Sincronización recuperable de operaciones de campo")
@SecurityRequirement(name="bearerAuth")
public class OfflineSyncController {
    private final OfflineSyncService sync;
    private final AuthService auth;

    public OfflineSyncController(OfflineSyncService sync,AuthService auth) {
        this.sync=sync; this.auth=auth;
    }

    @PostMapping(value="/batches",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary="Sincronizar un batch offline",
            description="Recibe manifest JSON y hasta 10 partes files identificadas por originalFilename. Ordena dependencias, ejecuta cada elemento en su propia transacción y conserva resultados terminales por 30 días.")
    @ApiResponse(responseCode="200",description="Resultado individual SYNCED, RETRYABLE o REQUIRES_ATTENTION")
    @ApiResponse(responseCode="400",description="Manifest, dependencias o archivos inválidos",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="401",description="Token ausente o inválido",content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="403",description="Solo el operador puede sincronizar",content=@Content(schema=@Schema(implementation=ApiError.class)))
    public OfflineSyncService.SyncBatchResponse synchronize(
            @RequestPart("manifest") @Valid OfflineSyncService.SyncBatchRequest manifest,
            @Parameter(description="Archivos cuyo originalFilename coincide con fileKey")
            @RequestPart(value="files",required=false) List<MultipartFile> files,
            Principal principal) {
        Map<String,MultipartFile> indexed=new LinkedHashMap<>();
        for(MultipartFile file:files==null?List.<MultipartFile>of():files) {
            String key=file.getOriginalFilename();
            if(key==null || key.isBlank() || indexed.putIfAbsent(key,file)!=null)
                throw new FieldValidationException("Batch de sincronización inválido",
                        Map.of("files",List.of("cada archivo requiere un originalFilename único")));
        }
        return sync.synchronize(manifest,indexed,auth.currentUser(principal.getName()));
    }
}
