package com.api.servicecompliance.executions.infrastructure;

import com.api.servicecompliance.executions.application.PhotoStorage;
import com.api.servicecompliance.executions.application.PhotoStorageUnavailableException;
import com.api.servicecompliance.executions.application.PhotoValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

@Component
public class SupabasePhotoStorage implements PhotoStorage {
    private final String baseUrl;
    private final String serviceKey;
    private final String bucket;
    private final HttpClient client;
    private final ObjectMapper json = new ObjectMapper();

    public SupabasePhotoStorage(@Value("${app.field-operations.storage.url:}") String url,
                                @Value("${app.field-operations.storage.service-key:}") String key,
                                @Value("${app.field-operations.storage.bucket:}") String bucket) {
        this.baseUrl=url.replaceAll("/+$", ""); this.serviceKey=key; this.bucket=bucket;
        this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public void put(String key, byte[] data, String type) {
        requirePrivateBucket();
        send(request("/object/" + bucket + "/" + safeKey(key))
                .header("Content-Type", type).header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(data)).build(), false);
    }
    @Override public byte[] get(String key) {
        requirePrivateBucket();
        return send(request("/object/authenticated/" + bucket + "/" + safeKey(key)).GET().build(), false);
    }
    @Override public void delete(String key) {
        requireConfiguration();
        send(request("/object/" + bucket + "/" + safeKey(key)).DELETE().build(), true);
    }

    private void requirePrivateBucket() {
        requireConfiguration();
        byte[] body = send(request("/bucket/" + bucket).GET().build(), false);
        try {
            var info = json.readTree(body).path("public");
            if (!info.isBoolean() || info.asBoolean()) throw new PhotoStorageUnavailableException();
        } catch (IOException exception) { throw new PhotoStorageUnavailableException(); }
    }

    private void requireConfiguration() {
        if (baseUrl.isBlank() || serviceKey.isBlank() || serviceKey.chars().anyMatch(c -> c<=32 || c>=127)
                || !bucket.matches("[A-Za-z0-9._-]+")) {
            throw new PhotoStorageUnavailableException();
        }
        try {
            URI uri=URI.create(baseUrl);
            boolean local="http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
            if ((!local && !"https".equals(uri.getScheme())) || uri.getHost()==null
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                throw new PhotoStorageUnavailableException();
            }
        } catch (IllegalArgumentException exception) { throw new PhotoStorageUnavailableException(); }
    }

    private String safeKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9/_-]+[.](png|jpg)") || key.contains("..")) {
            throw new PhotoStorageUnavailableException();
        }
        return key;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + "/storage/v1" + path))
                .timeout(Duration.ofSeconds(20)).header("apikey", serviceKey)
                .header("Authorization", "Bearer " + serviceKey);
    }

    private byte[] send(HttpRequest request, boolean allowMissing) {
        var pending=client.sendAsync(request, info -> new BoundedBody());
        try {
            // The timeout includes the complete response body, not only its headers.
            var response=pending.get(20, TimeUnit.SECONDS);
            if (allowMissing && response.statusCode()==404) return new byte[0];
            if (response.statusCode()<200 || response.statusCode()>=300) throw new PhotoStorageUnavailableException();
            return response.body();
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt(); throw new PhotoStorageUnavailableException();
        } catch (ExecutionException | TimeoutException exception) {
            pending.cancel(true); throw new PhotoStorageUnavailableException();
        }
    }

    private static class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription=subscription; subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer bytes:buffers) {
                if ((long)buffer.size()+bytes.remaining()>PhotoValidator.MAX_BYTES) {
                    subscription.cancel(); result.completeExceptionally(new PhotoStorageUnavailableException()); return;
                }
                byte[] chunk=new byte[bytes.remaining()]; bytes.get(chunk); buffer.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(buffer.toByteArray()); }
    }
}
