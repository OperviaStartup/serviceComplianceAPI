package com.api.servicecompliance.authentication.application;

public class ConflictException extends com.api.servicecompliance.shared.domain.ApiConflictException {
    public ConflictException(String code, String message) {
        super(code, message);
    }
}
