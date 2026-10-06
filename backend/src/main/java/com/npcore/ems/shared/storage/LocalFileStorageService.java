package com.npcore.ems.shared.storage;

import com.npcore.ems.config.EmsProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Lưu file trên đĩa (dev / máy đơn). Production dùng MinIO: ems.storage.type=minio. */
@Service
@ConditionalOnProperty(name = "ems.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements StorageService {

    private final Path root;
    private final String bucket;

    public LocalFileStorageService(EmsProperties props) {
        this.bucket = props.storage().bucket();
        this.root = Path.of(props.storage().localRoot()).toAbsolutePath().normalize().resolve(bucket);
    }

    @Override
    public String bucket() {
        return bucket;
    }

    @Override
    public void put(String key, Path source, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return p;
    }
}
