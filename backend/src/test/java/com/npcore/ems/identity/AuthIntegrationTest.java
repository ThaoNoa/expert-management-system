package com.npcore.ems.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthIntegrationTest extends AbstractIntegrationTest {

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void adminLoginReturnsTokensAndPermissions() {
        JsonNode body = loginJson(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertThat(body.path("accessToken").asText()).isNotBlank();
        assertThat(body.path("refreshToken").asText()).isNotBlank();
        assertThat(body.path("expiresIn").asLong()).isEqualTo(900);
        JsonNode user = body.path("user");
        assertThat(user.path("username").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(texts(user.path("roles"))).contains("SUPER_ADMIN");
        assertThat(texts(user.path("permissions"))).contains("USER_MANAGE", "USER_MANAGE:ALL", "SETTING_MANAGE:ALL");
        assertThat(user.path("expertId").isNull()).isTrue();
    }

    @Test
    void wrongPasswordIs401WithUnauthorizedCode() {
        TestUser u = createUserWithRoles("DOCUMENT_CONTROLLER");
        expectError(loginResponse(u.username, "Wrong#123"), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        expectError(loginResponse(uniq("nobody"), "Wrong#123"), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void usernameIsCaseInsensitiveAndTrimmed() {
        TestUser u = createUserWithRoles("EXPERT");
        assertThat(login("  " + u.username.toUpperCase() + " ", u.password)).isNotBlank();
    }

    @Test
    void fiveWrongAttemptsLockTheAccountUntilAdminEnablesIt() {
        TestUser u = createUserWithRoles("DOCUMENT_CONTROLLER");
        for (int i = 0; i < 4; i++) {
            expectError(loginResponse(u.username, "Wrong#123"), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
        // vẫn chưa khoá sau 4 lần
        assertThat(getJson("/users/" + u.id, adminToken()).path("status").asText()).isEqualTo("ACTIVE");
        expectError(loginResponse(u.username, "Wrong#123"), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertThat(getJson("/users/" + u.id, adminToken()).path("status").asText()).isEqualTo("LOCKED");

        // đúng mật khẩu cũng bị từ chối
        expectError(loginResponse(u.username, u.password), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");

        expect(post("/users/" + u.id + "/enable", adminToken(), null), HttpStatus.NO_CONTENT);
        assertThat(getJson("/users/" + u.id, adminToken()).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(login(u.username, u.password)).isNotBlank();
    }

    @Test
    void successfulLoginResetsFailedCounter() {
        TestUser u = createUserWithRoles("EXPERT");
        for (int i = 0; i < 4; i++) loginResponse(u.username, "Wrong#123");
        login(u.username, u.password);
        for (int i = 0; i < 4; i++) loginResponse(u.username, "Wrong#123");
        assertThat(getJson("/users/" + u.id, adminToken()).path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void refreshRotatesTokens() {
        TestUser u = createUserWithRoles("EXPERT");
        JsonNode first = loginJson(u.username, u.password);
        String refresh1 = first.path("refreshToken").asText();

        JsonNode second = postJson("/auth/refresh", null, Map.of("refreshToken", refresh1), HttpStatus.OK);
        String refresh2 = second.path("refreshToken").asText();
        assertThat(refresh2).isNotBlank().isNotEqualTo(refresh1);
        assertThat(second.path("accessToken").asText()).isNotBlank();
        expect(get("/auth/me", second.path("accessToken").asText()), HttpStatus.OK);

        // refresh token cũ chỉ dùng được một lần
        expectError(post("/auth/refresh", null, Map.of("refreshToken", refresh1)), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        // token mới vẫn dùng được
        postJson("/auth/refresh", null, Map.of("refreshToken", refresh2), HttpStatus.OK);
        expectError(post("/auth/refresh", null, Map.of("refreshToken", "not-a-token")), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void logoutRevokesRefreshToken() {
        TestUser u = createUserWithRoles("EXPERT");
        JsonNode tokens = loginJson(u.username, u.password);
        String refresh = tokens.path("refreshToken").asText();
        expect(post("/auth/logout", tokens.path("accessToken").asText(), Map.of("refreshToken", refresh)), HttpStatus.NO_CONTENT);
        expectError(post("/auth/refresh", null, Map.of("refreshToken", refresh)), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void tamperedOrMissingAccessTokenIs401() {
        String token = adminToken();
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        expectError(get("/auth/me", tampered), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        String badSig = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
        expectError(get("/auth/me", badSig), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        expectError(get("/users", "garbage"), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        expectError(get("/auth/me", null), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void meReturnsCurrentUser() {
        TestUser u = createUserWithRoles("DOCUMENT_CONTROLLER");
        JsonNode me = getJson("/auth/me", u.token());
        assertThat(me.path("id").asText()).isEqualTo(u.id.toString());
        assertThat(me.path("username").asText()).isEqualTo(u.username);
        assertThat(me.path("email").asText()).isEqualTo(u.email);
        assertThat(texts(me.path("roles"))).containsExactly("DOCUMENT_CONTROLLER");
        assertThat(texts(me.path("permissions"))).contains("DOCUMENT_VERIFY", "DOCUMENT_VERIFY:ALL")
                .doesNotContain("USER_MANAGE", "COMPETENCY_APPROVE");
    }

    @Test
    void changePassword() {
        TestUser u = createUserWithRoles("EXPERT");
        String token = u.token();
        expectError(post("/auth/change-password", token, Map.of("currentPassword", u.password, "newPassword", "short")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post("/auth/change-password", token, Map.of("currentPassword", u.password, "newPassword", "onlyletters")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post("/auth/change-password", token, Map.of("currentPassword", "Wrong#123", "newPassword", "N3w!Passw0rd")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expect(post("/auth/change-password", token, Map.of("currentPassword", u.password, "newPassword", "N3w!Passw0rd")),
                HttpStatus.NO_CONTENT);
        expectError(loginResponse(u.username, u.password), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertThat(login(u.username, "N3w!Passw0rd")).isNotBlank();
    }

    @Test
    void loginValidation() {
        expectError(post("/auth/login", null, Map.of("username", "", "password", "")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }
}
