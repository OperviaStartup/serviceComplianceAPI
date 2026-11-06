package com.api.servicecompliance;

import com.api.servicecompliance.executions.application.PhotoStorageUnavailableException;
import com.api.servicecompliance.executions.infrastructure.SupabasePhotoStorage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class SupabasePhotoStorageTests {
    private HttpServer server;
    private SupabasePhotoStorage storage;
    private final AtomicReference<String> uploadedType=new AtomicReference<>();
    private final AtomicReference<String> upsert=new AtomicReference<>();
    private final AtomicReference<byte[]> uploaded=new AtomicReference<>();
    private final AtomicReference<String> authorization=new AtomicReference<>();
    private final AtomicReference<String> objectPath=new AtomicReference<>();
    private String bucketReply="{\"public\":false}";
    private int objectStatus=200;
    private byte[] downloadBytes=new byte[]{1,2,3};
    private boolean deleted;

    @BeforeEach void setUp() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/storage/v1/", exchange -> {
            byte[] result;
            String path=exchange.getRequestURI().getPath();
            if(path.contains("/object/")) objectPath.set(path);
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            int status=200;
            if(path.equals("/storage/v1/bucket/photos")) result=bucketReply.getBytes(StandardCharsets.UTF_8);
            else if(exchange.getRequestMethod().equals("POST")) {
                uploaded.set(exchange.getRequestBody().readAllBytes());
                uploadedType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                upsert.set(exchange.getRequestHeaders().getFirst("x-upsert"));
                result="{}".getBytes(StandardCharsets.UTF_8); status=objectStatus;
            } else if(exchange.getRequestMethod().equals("DELETE")) {
                deleted=true; result="{}".getBytes(StandardCharsets.UTF_8);
            } else { result=downloadBytes; status=objectStatus; }
            exchange.sendResponseHeaders(status,result.length);
            try(var out=exchange.getResponseBody()) { out.write(result); }
            exchange.close();
        });
        server.start();
        storage=new SupabasePhotoStorage("http://127.0.0.1:"+server.getAddress().getPort(),"test-only-key","photos");
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void sendsActualBytesWithoutOverwritingAndDownloadsFromThePrivateEndpoint() {
        byte[] data={4,5,6}; storage.put("company/1/photo.png",data,"image/png");
        assertThat(uploaded.get()).containsExactly(data);
        assertThat(uploadedType.get()).isEqualTo("image/png");
        assertThat(upsert.get()).isEqualTo("false");
        assertThat(authorization.get()).isEqualTo("Bearer test-only-key");
        assertThat(storage.get("company/1/photo.png")).containsExactly(1,2,3);
        assertThat(objectPath.get()).isEqualTo("/storage/v1/object/authenticated/photos/company/1/photo.png");
        storage.delete("company/1/photo.png"); assertThat(deleted).isTrue();
    }
    @Test void refusesPublicOrUnverifiableBucketsBeforeUploading() {
        for(String reply:Arrays.asList("{\"public\":true}","{}","not json")) {
            bucketReply=reply;
            assertThatThrownBy(() -> storage.put("company/1/photo.png",new byte[]{1},"image/png"))
                    .isInstanceOf(PhotoStorageUnavailableException.class);
        }
        assertThat(uploaded.get()).isNull();
    }
    @Test void missingConfigurationAndProviderFailuresNeverReportSuccess() {
        assertThatThrownBy(() -> new SupabasePhotoStorage("","","").put("photo.png",new byte[]{1},"image/png"))
                .isInstanceOf(PhotoStorageUnavailableException.class);
        objectStatus=503;
        assertThatThrownBy(() -> storage.put("company/1/photo.png",new byte[]{1},"image/png"))
                .isInstanceOf(PhotoStorageUnavailableException.class).hasMessageNotContaining("test-only-key");
        assertThatThrownBy(() -> storage.get("company/1/photo.png")).isInstanceOf(PhotoStorageUnavailableException.class);
    }
    @Test void rejectsPathTraversalAndUntrustedRedirectSchemes() {
        assertThatThrownBy(() -> storage.put("../private.png",new byte[]{1},"image/png"))
                .isInstanceOf(PhotoStorageUnavailableException.class);
        assertThatThrownBy(() -> new SupabasePhotoStorage("http://example.test","test-only-key","photos")
                .put("photo.png",new byte[]{1},"image/png")).isInstanceOf(PhotoStorageUnavailableException.class);
    }
    @Test void refusesAnOversizedProviderResponse() {
        downloadBytes=new byte[com.api.servicecompliance.executions.application.PhotoValidator.MAX_BYTES+1];
        assertThatThrownBy(() -> storage.get("company/1/photo.png")).isInstanceOf(PhotoStorageUnavailableException.class);
    }
}
