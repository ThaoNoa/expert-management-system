package com.npcore.ems.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ems")
public record EmsProperties(Security security, Bootstrap bootstrap, Storage storage) {

    public record Security(String jwtSecret, int accessTokenMinutes, int refreshTokenDays,
                           int maxFailedLogins, List<String> corsOrigins) {}

    public record Bootstrap(String adminUsername, String adminPassword, String adminEmail) {}

    public record Storage(String type, String localRoot, String bucket, Minio minio) {
        public record Minio(String endpoint, String accessKey, String secretKey) {}
    }
}
