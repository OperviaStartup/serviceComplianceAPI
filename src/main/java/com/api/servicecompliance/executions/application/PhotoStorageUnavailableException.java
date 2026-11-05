package com.api.servicecompliance.executions.application;

public class PhotoStorageUnavailableException extends RuntimeException {
    public PhotoStorageUnavailableException() {
        super("El almacenamiento de fotografías no está disponible");
    }
}
