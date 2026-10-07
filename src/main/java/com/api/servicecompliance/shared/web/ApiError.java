package com.api.servicecompliance.shared.web;

import java.time.Instant;

public record ApiError(String code, String message, Instant timestamp) {}
