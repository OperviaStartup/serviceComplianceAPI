package com.api.servicecompliance.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ApiError", description = "Respuesta estándar para errores manejables de la API")
public record ApiError(
        @Schema(example = "VALIDATION_ERROR") String code,
        @Schema(example = "email: debe ser una dirección de correo válida") String message,
        @Schema(example = "2026-10-07T15:00:00Z") Instant timestamp) {}
