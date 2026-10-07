package com.api.servicecompliance.authentication.application;

import org.springframework.security.core.AuthenticationException;

public class InvalidCredentialsException extends AuthenticationException {
    public InvalidCredentialsException() { super("Credenciales inválidas"); }
}
