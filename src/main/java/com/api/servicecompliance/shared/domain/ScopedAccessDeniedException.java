package com.api.servicecompliance.shared.domain;

import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

public class ScopedAccessDeniedException extends AccessDeniedException {
    private final Map<String, List<String>> details;

    public ScopedAccessDeniedException(Map<String, List<String>> details) {
        super("Resource outside authenticated scope");
        this.details = Map.copyOf(details);
    }

    public Map<String, List<String>> getDetails() {
        return details;
    }
}
