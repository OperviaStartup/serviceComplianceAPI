package com.api.servicecompliance.shared.web;

import com.api.servicecompliance.shared.domain.DomainException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
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
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct().reduce((left, right) -> left + "; " + right).orElse("Datos inválidos");
        return new ApiError("VALIDATION_ERROR", message, Instant.now());
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

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiError handleUnexpected(Exception exception) {
        return new ApiError("INTERNAL_ERROR", "Ocurrió un error interno", Instant.now());
    }
}
