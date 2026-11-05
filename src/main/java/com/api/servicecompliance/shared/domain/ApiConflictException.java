package com.api.servicecompliance.shared.domain;

public class ApiConflictException extends RuntimeException {
    private final String code;
    private final Long currentVersion;

    public ApiConflictException(String code, String message) {
        this(code, message, null);
    }

    public ApiConflictException(String code, String message, Long currentVersion) {
        super(message);
        this.code = code;
        this.currentVersion = currentVersion;
    }

    public String getCode() {
        return code;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }
}
