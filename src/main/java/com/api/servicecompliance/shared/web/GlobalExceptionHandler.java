package com.api.servicecompliance.shared.web;

import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.DomainException;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import com.api.servicecompliance.shared.domain.ScopedAccessDeniedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleConflict(ApiConflictException exception) {
        return new ApiError(exception.getCode(), exception.getMessage(), Instant.now(), null,
                exception.getCurrentVersion());
    }

    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleDomain(DomainException exception) {
        return new ApiError("DOMAIN_ERROR", exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleNotFound(ResourceNotFoundException exception) {
        return new ApiError("RESOURCE_NOT_FOUND", exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleValidation(MethodArgumentNotValidException exception) {
        var details = new java.util.LinkedHashMap<String, java.util.List<String>>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                details.computeIfAbsent(error.getField(), ignored -> new java.util.ArrayList<>())
                        .add(error.getDefaultMessage()));
        return new ApiError("VALIDATION_ERROR", "Datos inválidos", Instant.now(), details, null);
    }

    @ExceptionHandler(FieldValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleFieldValidation(FieldValidationException exception) {
        return new ApiError("VALIDATION_ERROR", exception.getMessage(), Instant.now(),
                exception.getDetails(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleMalformedJson(HttpMessageNotReadableException exception) {
        return new ApiError("MALFORMED_JSON", "El cuerpo de la solicitud no tiene un formato válido", Instant.now());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiError handleAuthentication(AuthenticationException exception) {
        return new ApiError("UNAUTHORIZED", "La autenticación es inválida o ha expirado", Instant.now());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ApiError handleAccessDenied(AccessDeniedException exception) {
        return new ApiError("FORBIDDEN", "El usuario no tiene permisos para realizar esta operación", Instant.now());
    }

    @ExceptionHandler(ScopedAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ApiError handleScopedAccessDenied(ScopedAccessDeniedException exception) {
        return new ApiError("FORBIDDEN", "El recurso no pertenece al alcance autorizado", Instant.now(),
                exception.getDetails(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiError handleUnexpected(Exception exception) {
        return new ApiError("INTERNAL_ERROR", "Ocurrió un error interno", Instant.now());
    }
}
