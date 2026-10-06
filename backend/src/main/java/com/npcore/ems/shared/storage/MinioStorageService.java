package com.npcore.ems.shared.storage;

import com.npcore.ems.config.EmsProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import java.io.InputStream;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "ems.storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private final MinioClient client;
    private final String bucket;

    public MinioStorageService(EmsProperties props) {
        var m = props.storage().minio();
        this.client = MinioClient.builder().endpoint(m.endpoint()).credentials(m.accessKey(), m.secretKey()).build();
        this.bucket = props.storage().bucket();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không kết nối được MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public String bucket() {
        return bucket;
    }

    @Override
    public void put(String key, Path source, String contentType) {
        try {
            client.uploadObject(UploadObjectArgs.builder().bucket(bucket).object(key)
                    .filename(source.toString()).contentType(contentType).build());
        } catch (Exception e) {
            throw new IllegalStateException("Upload MinIO thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("Đọc MinIO thất bại: " + e.getMessage(), e);
        }
    }
}
