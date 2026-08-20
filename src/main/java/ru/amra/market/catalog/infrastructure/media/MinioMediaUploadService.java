package ru.amra.market.catalog.infrastructure.media;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.RemoveObjectArgs;
import java.net.URI;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.amra.market.catalog.application.MediaUploadService;
import ru.amra.market.catalog.domain.ProductId;

/** MinIO/S3-compatible direct-upload adapter. */
@Service
class MinioMediaUploadService implements MediaUploadService {

    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final MinioClient minio;
    private final MediaStorageProperties properties;
    private final Clock clock;

    MinioMediaUploadService(MinioClient minio, MediaStorageProperties properties, Clock clock) {
        this.minio = minio;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Upload prepare(ProductId productId, String fileName, String contentType, long size) {
        if (!CONTENT_TYPES.contains(contentType) || size < 1 || size > properties.maxSize()) {
            throw new IllegalArgumentException("Unsupported catalog media upload");
        }
        var extension = extension(fileName, contentType);
        var objectKey = "products/" + productId.value() + "/uploads/" + UUID.randomUUID() + extension;
        try {
            ensureBucket();
            var seconds = Math.toIntExact(properties.uploadTtl().toSeconds());
            var url = minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry(seconds)
                    .build());
            return new Upload(
                    objectKey,
                    URI.create(url),
                    clock.instant().plus(properties.uploadTtl()),
                    contentType,
                    properties.maxSize());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare catalog media upload", exception);
        }
    }

    @Override
    public void verify(ProductId productId, String objectKey, String contentType) {
        if (!objectKey.startsWith("products/" + productId.value() + "/uploads/")) {
            throw new IllegalArgumentException("Catalog media object does not belong to product");
        }
        try {
            var object = minio.statObject(StatObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
            if (object.size() < 1
                    || object.size() > properties.maxSize()
                    || !contentType.equals(object.contentType())) {
                throw new IllegalArgumentException("Uploaded catalog media metadata mismatch");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Uploaded catalog media object is unavailable", exception);
        }
    }

    @Override
    public void delete(Iterable<String> objectKeys) {
        try {
            for (var objectKey : objectKeys) {
                minio.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot remove catalog media", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (!minio.bucketExists(
                BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
            minio.makeBucket(
                    MakeBucketArgs.builder().bucket(properties.bucket()).build());
        }
    }

    private static String extension(String fileName, String contentType) {
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("Catalog media file name must not be blank");
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Unsupported catalog media content type");
        };
    }
}
