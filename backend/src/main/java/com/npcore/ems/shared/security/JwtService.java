package com.npcore.ems.shared.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.npcore.ems.config.EmsProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Access token JWT ký HS256 (RFC 7519) - cài đặt gọn, chỉ chấp nhận đúng alg HS256 (chặn "alg=none" / đổi thuật toán).
 * Refresh token là chuỗi ngẫu nhiên, lưu hash trong DB (xem AuthService).
 */
@Service
public class JwtService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final long accessTtlSeconds;
    private final ObjectMapper mapper;

    public JwtService(EmsProperties props, ObjectMapper mapper) {
        this.secret = props.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("ems.security.jwt-secret phải dài tối thiểu 32 byte");
        }
        this.accessTtlSeconds = props.security().accessTokenMinutes() * 60L;
        this.mapper = mapper;
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    public String issueAccessToken(UUID userId, String username, Collection<String> permissions) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId.toString());
        claims.put("username", username);
        claims.put("perms", List.copyOf(permissions));
        claims.put("iat", now);
        claims.put("exp", now + accessTtlSeconds);
        try {
            String payload = B64.encodeToString(mapper.writeValueAsBytes(claims));
            String signingInput = HEADER + "." + payload;
            return signingInput + "." + B64.encodeToString(hmac(signingInput));
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được token", e);
        }
    }

    public Optional<CurrentUser> parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !HEADER.equals(parts[0])) return Optional.empty();
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, B64D.decode(parts[2]))) return Optional.empty();
            Map<String, Object> c = mapper.readValue(B64D.decode(parts[1]), new TypeReference<>() {});
            long exp = ((Number) c.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) return Optional.empty();
            @SuppressWarnings("unchecked")
            List<String> perms = (List<String>) c.getOrDefault("perms", List.of());
            return Optional.of(new CurrentUser(UUID.fromString((String) c.get("sub")), (String) c.get("username"),
                    new HashSet<>(perms)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String input) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
    }
}
