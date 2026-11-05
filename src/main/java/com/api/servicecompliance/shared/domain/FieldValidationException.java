package com.api.servicecompliance.shared.domain;

import java.util.List;
import java.util.Map;

public class FieldValidationException extends RuntimeException {
    private final Map<String, List<String>> details;

    public FieldValidationException(String message, Map<String, List<String>> details) {
        super(message);
        this.details = Map.copyOf(details);
    }

    public Map<String, List<String>> getDetails() {
        return details;
    }
}
