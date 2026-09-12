package ru.amra.market.catalog.infrastructure.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.amra.market.catalog.domain.ProductId;

class MinioMediaUploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
    private static final UUID ID = UUID.fromString("01991a80-0000-7000-8000-000000000301");
    private MinioClient minio;
    private MinioMediaUploadService service;

    @BeforeEach
    void setUp() throws Exception {
        minio = mock(MinioClient.class);
        when(minio.bucketExists(any())).thenReturn(true);
        service = new MinioMediaUploadService(
                minio,
                new MediaStorageProperties(
                        "http://localhost:9000",
                        "http://localhost:9000",
                        "test-access-key",
                        "test-secret-key",
                        "us-east-1",
                        "catalog",
                        Duration.ofMinutes(10),
                        1_000),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void preparesProductAndBannerUploadsWithBoundedMetadata() {
        var product = service.prepare(new ProductId(ID), "photo.jpeg", "image/jpeg", 800);
        var banner = service.prepareBanner(ID, "hero.webp", "image/webp", 900);

        assertThat(product.objectKey())
                .startsWith("products/" + ID + "/uploads/")
                .endsWith(".jpg");
        assertThat(banner.objectKey()).startsWith("banners/" + ID + "/uploads/").endsWith(".webp");
        assertThat(product.uploadUrl()).hasScheme("http");
        assertThat(product.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(product.maxSize()).isEqualTo(1_000);
    }

    @Test
    void rejectsUnsupportedUploadInputs() {
        var productId = new ProductId(ID);
        assertThatThrownBy(() -> service.prepare(productId, "file.gif", "image/gif", 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.prepare(productId, "file.png", "image/png", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.prepareBanner(ID, "file.png", "image/png", 1_001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.prepare(productId, " ", "image/png", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file name");
    }

    @Test
    void verifiesOwnershipAndStoredMetadata() throws Exception {
        var productKey = "products/" + ID + "/uploads/photo.png";
        var bannerKey = "banners/" + ID + "/uploads/hero.webp";
        var productObject = mock(StatObjectResponse.class);
        when(productObject.size()).thenReturn(900L);
        when(productObject.contentType()).thenReturn("image/png");
        var bannerObject = mock(StatObjectResponse.class);
        when(bannerObject.size()).thenReturn(900L);
        when(bannerObject.contentType()).thenReturn("image/webp");
        when(minio.statObject(any())).thenReturn(productObject, bannerObject);

        service.verify(new ProductId(ID), productKey, "image/png");
        service.verifyBanner(ID, bannerKey);

        assertThatThrownBy(() -> service.verify(new ProductId(UUID.randomUUID()), productKey, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        assertThatThrownBy(() -> service.verifyBanner(null, "products/foreign.webp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key is invalid");
    }

    @Test
    void rejectsMismatchedOrUnavailableObjectsAndWrapsDeleteFailure() throws Exception {
        var object = mock(StatObjectResponse.class);
        when(object.size()).thenReturn(0L);
        when(object.contentType()).thenReturn("image/png");
        when(minio.statObject(any())).thenReturn(object);
        var productKey = "products/" + ID + "/uploads/photo.png";

        assertThatThrownBy(() -> service.verify(new ProductId(ID), productKey, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata mismatch");

        when(minio.statObject(any())).thenThrow(new RuntimeException("storage unavailable"));
        assertThatThrownBy(() -> service.verifyBanner(ID, "banners/" + ID + "/uploads/hero.webp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unavailable");

        doThrow(new RuntimeException("delete failed")).when(minio).removeObject(any(RemoveObjectArgs.class));
        assertThatThrownBy(() -> service.delete(List.of(productKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot remove");
    }

    @Test
    void rejectsOversizedWrongTypeAndForeignBannerObjects() throws Exception {
        var object = mock(StatObjectResponse.class);
        when(object.size()).thenReturn(1_001L, 100L, 1_001L);
        when(object.contentType()).thenReturn("text/plain");
        when(minio.statObject(any())).thenReturn(object);
        var productKey = "products/" + ID + "/uploads/photo.png";
        var bannerKey = "banners/" + ID + "/uploads/hero.webp";

        assertThatThrownBy(() -> service.verify(new ProductId(ID), productKey, "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verify(new ProductId(ID), productKey, "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verifyBanner(ID, bannerKey)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verifyBanner(UUID.randomUUID(), bannerKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void createsMissingBucketAndDeletesEveryRequestedObject() throws Exception {
        when(minio.bucketExists(any())).thenReturn(false);

        service.prepare(new ProductId(ID), "photo.png", "image/png", 100);
        service.delete(List.of("one", "two"));

        verify(minio).makeBucket(any());
        verify(minio, org.mockito.Mockito.times(2)).removeObject(any(RemoveObjectArgs.class));
    }
}
