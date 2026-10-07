package com.api.servicecompliance.shared.web;

import com.api.servicecompliance.shared.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleDomain(DomainException exception) {
        return new ApiError("DOMAIN_ERROR", exception.getMessage(), Instant.now());
    }
}
