package com.npcore.ems.shared.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String HIGH_DAYS = "alert.expiry.highDays";

    private JsonNode setting(String key) {
        for (JsonNode s : getJson("/settings", adminToken())) {
            if (s.path("key").asText().equals(key)) return s;
        }
        throw new AssertionError("Không thấy setting " + key);
    }

    @Test
    void listSettingsAsAdmin() {
        JsonNode all = getJson("/settings", adminToken());
        assertThat(all.size()).isGreaterThanOrEqualTo(10);
        JsonNode high = setting(HIGH_DAYS);
        assertThat(high.path("valueType").asText()).isEqualTo("INT");
        assertThat(high.path("category").asText()).isEqualTo("ALERT");
        assertThat(high.path("value").isNumber()).isTrue();

        TestUser manager = createUserWithRoles("CERTIFICATION_MANAGER");
        expectError(get("/settings", manager.token()), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(put("/settings/" + HIGH_DAYS, manager.token(), Map.of("value", 10)), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void updateValidatesTypeAndUnknownKey() {
        expectError(put("/settings/" + HIGH_DAYS, adminToken(), Map.of("value", "abc")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(put("/settings/" + HIGH_DAYS, adminToken(), Map.of("value", true)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(put("/settings/" + HIGH_DAYS, adminToken(), "{}"), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(put("/settings/no.such.key", adminToken(), Map.of("value", 1)), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void highDaysThresholdDrivesCertificateExpiryLevel() {
        TestUser manager = createUserWithRoles("CERTIFICATION_MANAGER");
        String expertId = createExpert(manager.token(), "Settings " + uniq(""), "FULLTIME", null).path("id").asText();
        Map<String, Object> cert = new HashMap<>();
        cert.put("certificateName", "ISO 14001 LA");
        cert.put("expiryDate", LocalDate.now().plusDays(20).toString());
        String certPath = "/experts/" + expertId + "/certificates";
        JsonNode created = postJson(certPath, manager.token(), cert, HttpStatus.CREATED);

        JsonNode original = setting(HIGH_DAYS).path("value");
        try {
            assertThat(created.path("expiryLevel").asText()).isEqualTo("HIGH");     // 20 <= 30

            JsonNode updated = putJson("/settings/" + HIGH_DAYS, adminToken(), Map.of("value", 10));
            assertThat(updated.path("value").asInt()).isEqualTo(10);
            assertThat(setting(HIGH_DAYS).path("value").asInt()).isEqualTo(10);
            JsonNode afterLower = getJson(certPath, manager.token()).get(0);
            assertThat(afterLower.path("expiryLevel").asText()).isEqualTo("WARNING");   // 20 > 10, <= 60

            putJson("/settings/" + HIGH_DAYS, adminToken(), Map.of("value", "25"));     // INT dạng chuỗi số
            assertThat(getJson(certPath, manager.token()).get(0).path("expiryLevel").asText()).isEqualTo("HIGH");

            JsonNode audit = getJson("/audit-logs?objectType=SYSTEM_SETTING&objectId=" + HIGH_DAYS, adminToken());
            assertThat(audit.path("totalElements").asLong()).isGreaterThanOrEqualTo(2);
        } finally {
            putJson("/settings/" + HIGH_DAYS, adminToken(), Map.of("value", original));
        }
        assertThat(setting(HIGH_DAYS).path("value")).isEqualTo(original);
        assertThat(getJson(certPath, manager.token()).get(0).path("expiryLevel").asText()).isEqualTo("HIGH");
    }
}
