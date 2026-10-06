package com.npcore.ems.shared.storage;

import java.io.InputStream;
import java.nio.file.Path;

/** Lưu file nhị phân ngoài DB (NFR-ST-01/02). */
public interface StorageService {
    /** Bucket logic lưu kèm metadata trong DB. */
    String bucket();

    void put(String key, Path source, String contentType);

    InputStream get(String key);
}
