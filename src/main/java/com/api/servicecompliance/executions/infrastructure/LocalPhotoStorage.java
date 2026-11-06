package com.api.servicecompliance.executions.infrastructure;

import com.api.servicecompliance.executions.application.PhotoStorage;
import com.api.servicecompliance.executions.application.PhotoStorageUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Real private files for an explicitly selected local demonstration, never a production fallback. */
@Component
@Primary
@Profile("field-local")
public class LocalPhotoStorage implements PhotoStorage {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final Path root;

    public LocalPhotoStorage(@Value("${app.field-operations.local-directory}") String directory) {
        try {
            Path configured = Path.of(directory);
            if (!configured.isAbsolute()) throw new IllegalArgumentException("Local photo directory must be absolute");
            Files.createDirectories(configured);
            root = configured.toRealPath();
        } catch (IOException exception) { throw new PhotoStorageUnavailableException(); }
    }

    @Override public void put(String key, byte[] data, String contentType) {
        if (data.length == 0 || data.length > MAX_BYTES) throw new PhotoStorageUnavailableException();
        Path target = resolve(key);
        boolean created = false;
        try {
            Path directory = root;
            for (Path part : root.relativize(target.getParent())) {
                directory = directory.resolve(part);
                if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory);
                if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Invalid local directory");
            }
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                output.write(data);
            }
        } catch (IOException exception) {
            if (created) { try { Files.deleteIfExists(target); } catch (IOException ignored) { } }
            throw new PhotoStorageUnavailableException();
        }
    }

    @Override public byte[] get(String key) {
        Path target = resolve(key);
        try {
            requirePrivatePath(target);
            if (Files.size(target) > MAX_BYTES) throw new IOException("Oversized local file");
            return Files.readAllBytes(target);
        } catch (IOException exception) { throw new PhotoStorageUnavailableException(); }
    }

    @Override public void delete(String key) {
        Path target = resolve(key);
        try {
            requirePrivatePath(target);
            Files.deleteIfExists(target);
        } catch (IOException exception) { throw new PhotoStorageUnavailableException(); }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("company/[0-9]+/site/[0-9]+/execution/[0-9]+/requirement/[0-9]+/[a-fA-F0-9-]{36}\\.(jpg|png)"))
            throw new PhotoStorageUnavailableException();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new PhotoStorageUnavailableException();
        return target;
    }

    private void requirePrivatePath(Path target) throws IOException {
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic links are not allowed");
        }
    }
}
