package com.api.servicecompliance.executions.application;

/** Private object storage. Implementations never return credentials or public object URLs. */
public interface PhotoStorage {
    void put(String key, byte[] data, String contentType);
    byte[] get(String key);
    void delete(String key);
}
