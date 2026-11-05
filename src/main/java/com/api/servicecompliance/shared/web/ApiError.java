package com.api.servicecompliance.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(name = "ApiError", description = "Respuesta estándar para errores manejables de la API")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        @Schema(example = "VALIDATION_ERROR") String code,
        @Schema(example = "email: debe ser una dirección de correo válida") String message,
        @Schema(example = "2026-10-07T15:00:00Z") Instant timestamp,
        Map<String, List<String>> details,
        Long currentVersion) {
    public ApiError(String code, String message, Instant timestamp) {
        this(code, message, timestamp, null, null);
    }
}
