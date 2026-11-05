package com.api.servicecompliance;

import com.api.servicecompliance.executions.infrastructure.LocalPhotoStorage;
import com.api.servicecompliance.executions.application.PhotoStorageUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalPhotoStorageTests {
    @TempDir Path directory;
    private final String key = "company/1/site/1/execution/1/requirement/1/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";

    @Test void filesSurviveAdapterRecreationAndCanBeDeleted() {
        var storage = new LocalPhotoStorage(directory.toString());
        byte[] bytes = {1, 2, 3};
        storage.put(key, bytes, "image/jpeg");
        assertArrayEquals(bytes, new LocalPhotoStorage(directory.toString()).get(key));
        storage.delete(key);
        storage.delete(key);
        assertThrows(PhotoStorageUnavailableException.class, () -> storage.get(key));
    }
    @Test void anExistingObjectCannotBeReplaced() {
        var storage = new LocalPhotoStorage(directory.toString());
        storage.put(key, new byte[]{1}, "image/jpeg");
        assertThrows(PhotoStorageUnavailableException.class, () -> storage.put(key, new byte[]{2}, "image/jpeg"));
        assertArrayEquals(new byte[]{1}, storage.get(key));
    }
    @Test void invalidKeysAndOversizedFilesAreRejected() {
        var storage = new LocalPhotoStorage(directory.toString());
        for (String unsafe : new String[]{"../photo.jpg", "C:/photo.jpg", key.replace("company/1", "company/../1")})
            assertThrows(PhotoStorageUnavailableException.class, () -> storage.put(unsafe, new byte[]{1}, "image/jpeg"));
        assertThrows(PhotoStorageUnavailableException.class, () -> storage.put(key, new byte[5*1024*1024+1], "image/jpeg"));
    }
    @Test void theAdapterIsAbsentWithoutTheExplicitLocalProfile() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(LocalPhotoStorage.class);
            context.refresh();
            assertTrue(context.getBeansOfType(LocalPhotoStorage.class).isEmpty());
        }
    }
}
