package com.npcore.ems.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.npcore.ems.config.EmsProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-unit-test-secret-64-bytes!!!!";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final ObjectMapper mapper = new ObjectMapper();

    private JwtService service(String secret, int accessMinutes) {
        var props = new EmsProperties(new EmsProperties.Security(secret, accessMinutes, 7, 5, List.of()), null, null);
        return new JwtService(props, mapper);
    }

    private static String sign(String signingInput, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return B64.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validTokenParsesBackToUser() {
        JwtService jwt = service(SECRET, 15);
        UUID id = UUID.randomUUID();
        String token = jwt.issueAccessToken(id, "alice", List.of("EXPERT_VIEW", "EXPERT_VIEW:ALL"));
        var user = jwt.parse(token).orElseThrow();
        assertThat(user.id()).isEqualTo(id);
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.permissions()).containsExactlyInAnyOrder("EXPERT_VIEW", "EXPERT_VIEW:ALL");
        assertThat(user.hasAll("EXPERT_VIEW")).isTrue();
        assertThat(jwt.accessTtlSeconds()).isEqualTo(900);
    }

    @Test
    void modifiedPayloadIsRejected() throws Exception {
        JwtService jwt = service(SECRET, 15);
        String token = jwt.issueAccessToken(UUID.randomUUID(), "bob", List.of("EXPERT_VIEW"));
        String[] p = token.split("\\.");
        String payload = new String(B64D.decode(p[1]), StandardCharsets.UTF_8)
                .replace("\"EXPERT_VIEW\"", "\"USER_MANAGE\"");
        String forged = p[0] + "." + B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + p[2];
        assertThat(jwt.parse(forged)).isEmpty();
    }

    @Test
    void modifiedSignatureIsRejected() {
        JwtService jwt = service(SECRET, 15);
        String token = jwt.issueAccessToken(UUID.randomUUID(), "bob", List.of());
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThat(jwt.parse(tampered)).isEmpty();
        assertThat(jwt.parse(token.substring(0, token.lastIndexOf('.')) + ".")).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String other = service(SECRET.replace('u', 'x'), 15).issueAccessToken(UUID.randomUUID(), "eve", List.of());
        assertThat(service(SECRET, 15).parse(other)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService issuer = service(SECRET, -1);                // exp = now - 60s
        String token = issuer.issueAccessToken(UUID.randomUUID(), "old", List.of());
        assertThat(service(SECRET, 15).parse(token)).isEmpty();
    }

    @Test
    void otherAlgorithmHeadersAreRejectedEvenWhenSignedCorrectly() throws Exception {
        JwtService jwt = service(SECRET, 15);
        String token = jwt.issueAccessToken(UUID.randomUUID(), "mallory", List.of("USER_MANAGE"));
        String payload = token.split("\\.")[1];
        for (String header : List.of("{\"alg\":\"none\",\"typ\":\"JWT\"}", "{\"alg\":\"HS512\",\"typ\":\"JWT\"}",
                "{\"typ\":\"JWT\",\"alg\":\"HS256\"}")) {
            String h = B64.encodeToString(header.getBytes(StandardCharsets.UTF_8));
            String input = h + "." + payload;
            assertThat(jwt.parse(input + "." + sign(input, SECRET))).as(header).isEmpty();
            assertThat(jwt.parse(input + ".")).as(header + " unsigned").isEmpty();
        }
    }

    @Test
    void garbageIsRejected() {
        JwtService jwt = service(SECRET, 15);
        assertThat(jwt.parse("")).isEmpty();
        assertThat(jwt.parse("a.b")).isEmpty();
        assertThat(jwt.parse("a.b.c.d")).isEmpty();
        assertThat(jwt.parse("!!!.???.###")).isEmpty();
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> service("too-short", 15)).isInstanceOf(IllegalStateException.class);
    }
}
