package com.npcore.ems.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UserRoleIntegrationTest extends AbstractIntegrationTest {

    private Map<String, Object> userBody(String username, String email, String... roles) {
        Map<String, Object> b = new HashMap<>();
        b.put("username", username);
        b.put("email", email);
        b.put("fullName", "Người dùng " + username);
        b.put("password", DEFAULT_PASSWORD);
        b.put("roleCodes", List.of(roles));
        return b;
    }

    private JsonNode findRole(String code) {
        for (JsonNode r : getJson("/roles", adminToken())) {
            if (r.path("roleCode").asText().equals(code)) return r;
        }
        throw new AssertionError("Không thấy role " + code);
    }

    @Test
    void createUserAndAuditLogIsWritten() {
        String username = uniq("create");
        JsonNode created = postJson("/users", adminToken(), userBody(username, username + "@Test.Local", "DOCUMENT_CONTROLLER"),
                HttpStatus.CREATED);
        String id = created.path("id").asText();
        assertThat(created.path("username").asText()).isEqualTo(username);
        assertThat(created.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.path("roleCodes").get(0).asText()).isEqualTo("DOCUMENT_CONTROLLER");
        assertThat(created.has("passwordHash")).isFalse();

        JsonNode fetched = getJson("/users/" + id, adminToken());
        assertThat(fetched.path("email").asText()).isEqualTo(username + "@Test.Local");

        JsonNode logs = getJson("/audit-logs?objectType=USER&objectId=" + id, adminToken());
        assertThat(logs.path("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode entry = logs.path("content").get(0);
        assertThat(entry.path("action").asText()).isEqualTo("CREATE");
        assertThat(entry.path("username").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(entry.path("toValue").path("username").asText()).isEqualTo(username);

        JsonNode page = getJson("/users?q=" + username, adminToken());
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() {
        String a = uniq("dupa");
        postJson("/users", adminToken(), userBody(a, a + "@test.local", "EXPERT"), HttpStatus.CREATED);
        String b = uniq("dupb");
        expectError(post("/users", adminToken(), userBody(b, a.toUpperCase() + "@TEST.LOCAL", "EXPERT")),
                HttpStatus.CONFLICT, "DUPLICATE");
        expectError(post("/users", adminToken(), userBody(a.toUpperCase(), b + "@test.local", "EXPERT")),
                HttpStatus.CONFLICT, "DUPLICATE");
    }

    @Test
    void createUserValidation() {
        String u = uniq("val");
        expectError(post("/users", adminToken(), userBody(u, "not-an-email", "EXPERT")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post("/users", adminToken(), userBody("có dấu " + u, u + "@x.vn", "EXPERT")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post("/users", adminToken(), userBody(u, u + "@x.vn", "NO_SUCH_ROLE")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        Map<String, Object> weak = userBody(u, u + "@x.vn", "EXPERT");
        weak.put("password", "12345678");
        expectError(post("/users", adminToken(), weak), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void roleBasedAccessToUserManagement() {
        TestUser dc = createUserWithRoles("DOCUMENT_CONTROLLER");
        expectError(get("/users", dc.token()), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(get("/roles", dc.token()), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(post("/users", dc.token(), userBody(uniq("x"), uniq("x") + "@x.vn", "EXPERT")),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(get("/settings", dc.token()), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(get("/audit-logs", dc.token()), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expect(get("/users", adminToken()), HttpStatus.OK);
    }

    @Test
    void disabledUserCannotLogInAndCanBeReenabled() {
        TestUser u = createUserWithRoles("EXPERT");
        JsonNode tokens = loginJson(u.username, u.password);
        expect(post("/users/" + u.id + "/disable", adminToken(), null), HttpStatus.NO_CONTENT);
        assertThat(getJson("/users/" + u.id, adminToken()).path("status").asText()).isEqualTo("DISABLED");
        expectError(loginResponse(u.username, u.password), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        // refresh token bị thu hồi khi vô hiệu hoá
        expectError(post("/auth/refresh", null, Map.of("refreshToken", tokens.path("refreshToken").asText())),
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        expect(post("/users/" + u.id + "/enable", adminToken(), null), HttpStatus.NO_CONTENT);
        assertThat(login(u.username, u.password)).isNotBlank();
    }

    @Test
    void adminCannotDisableOrDeleteHimself() {
        String adminId = getJson("/auth/me", adminToken()).path("id").asText();
        expectError(post("/users/" + adminId + "/disable", adminToken(), null), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
        expectError(delete("/users/" + adminId, adminToken()), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
    }

    @Test
    void softDeleteHidesUserAndBlocksLogin() {
        TestUser u = createUserWithRoles("EXPERT");
        expect(delete("/users/" + u.id, adminToken()), HttpStatus.NO_CONTENT);
        expectError(get("/users/" + u.id, adminToken()), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(getJson("/users?q=" + u.username, adminToken()).path("totalElements").asLong()).isZero();
        expectError(loginResponse(u.username, u.password), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        // email của user đã xoá được dùng lại
        String again = uniq("again");
        postJson("/users", adminToken(), userBody(again, u.email, "EXPERT"), HttpStatus.CREATED);
        JsonNode logs = getJson("/audit-logs?objectType=USER&action=DELETE&objectId=" + u.id, adminToken());
        assertThat(logs.path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void updateUserAndResetPassword() {
        TestUser u = createUserWithRoles("EXPERT");
        Map<String, Object> upd = Map.of("email", uniq("new") + "@test.local", "fullName", "Tên Mới",
                "roleCodes", List.of("EXPERT", "DOCUMENT_CONTROLLER"));
        JsonNode updated = putJson("/users/" + u.id, adminToken(), upd);
        assertThat(updated.path("fullName").asText()).isEqualTo("Tên Mới");
        List<String> roles = new ArrayList<>();
        updated.path("roleCodes").forEach(r -> roles.add(r.asText()));
        assertThat(roles).containsExactly("DOCUMENT_CONTROLLER", "EXPERT");

        expectError(post("/users/" + u.id + "/reset-password", adminToken(), Map.of("newPassword", "weak")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expect(post("/users/" + u.id + "/reset-password", adminToken(), Map.of("newPassword", "Res3t!pass")), HttpStatus.NO_CONTENT);
        assertThat(login(u.username, "Res3t!pass")).isNotBlank();
    }

    @Test
    void cannotDeleteSystemRoleOrRoleInUse() {
        JsonNode expertRole = findRole("EXPERT");
        assertThat(expertRole.path("system").asBoolean()).isTrue();
        expectError(delete("/roles/" + expertRole.path("id").asText(), adminToken()), HttpStatus.CONFLICT, "CONFLICT");

        String code = uniq("ROLE_").toUpperCase();
        JsonNode custom = postJson("/roles", adminToken(), Map.of("roleCode", code, "roleName", "Custom",
                "permissions", List.of(Map.of("code", "EXPERT_VIEW", "dataScope", "ALL"))), HttpStatus.CREATED);
        TestUser u = createUserWithRoles(code);
        expectError(delete("/roles/" + custom.path("id").asText(), adminToken()), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(u.id).isNotNull();
    }

    @Test
    void customRoleCreateUpdateDeleteWithGrants() {
        String code = uniq("ROLE_").toUpperCase();
        JsonNode created = postJson("/roles", adminToken(), Map.of("roleCode", code, "roleName", "Kiểm soát viên",
                "description", "test", "permissions", List.of(
                        Map.of("code", "EXPERT_VIEW", "dataScope", "ALL"),
                        Map.of("code", "DOCUMENT_VERIFY", "dataScope", "DEPARTMENT"))), HttpStatus.CREATED);
        String id = created.path("id").asText();
        assertThat(created.path("system").asBoolean()).isFalse();
        assertThat(created.path("permissions")).hasSize(2);

        // trùng mã
        expectError(post("/roles", adminToken(), Map.of("roleCode", code, "roleName", "x", "permissions", List.of())),
                HttpStatus.CONFLICT, "DUPLICATE");
        // quyền không tồn tại / scope sai
        expectError(post("/roles", adminToken(), Map.of("roleCode", code + "_B", "roleName", "x",
                "permissions", List.of(Map.of("code", "NOPE", "dataScope", "ALL")))), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post("/roles", adminToken(), Map.of("roleCode", code + "_C", "roleName", "x",
                "permissions", List.of(Map.of("code", "EXPERT_VIEW", "dataScope", "WORLD")))), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        // user nhận quyền theo role tuỳ chỉnh
        TestUser u = createUserWithRoles(code);
        List<String> perms = new ArrayList<>();
        getJson("/auth/me", u.token()).path("permissions").forEach(p -> perms.add(p.asText()));
        assertThat(perms).contains("EXPERT_VIEW", "EXPERT_VIEW:ALL", "DOCUMENT_VERIFY").doesNotContain("DOCUMENT_VERIFY:ALL");

        // cập nhật: giữ một quyền (đổi scope), bỏ một, thêm một
        JsonNode updated = putJson("/roles/" + id, adminToken(), Map.of("roleCode", code, "roleName", "Đổi tên",
                "permissions", List.of(Map.of("code", "EXPERT_VIEW", "dataScope", "OWN"),
                        Map.of("code", "REPORT_VIEW", "dataScope", "ALL"))));
        assertThat(updated.path("roleName").asText()).isEqualTo("Đổi tên");
        Map<String, String> grants = new HashMap<>();
        updated.path("permissions").forEach(g -> grants.put(g.path("code").asText(), g.path("dataScope").asText()));
        assertThat(grants).containsExactlyInAnyOrderEntriesOf(Map.of("EXPERT_VIEW", "OWN", "REPORT_VIEW", "ALL"));
        assertThat(findRole(code).path("permissions")).hasSize(2);

        // không đổi được mã role
        expectError(put("/roles/" + id, adminToken(), Map.of("roleCode", code + "X", "roleName", "x", "permissions", List.of())),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        // gỡ role khỏi user (user vẫn còn role khác) rồi xoá role
        putJson("/users/" + u.id, adminToken(), Map.of("email", u.email, "fullName", "x", "roleCodes", List.of("EXPERT")));
        expect(delete("/roles/" + id, adminToken()), HttpStatus.NO_CONTENT);
        assertThat(getJson("/audit-logs?objectType=ROLE&objectId=" + id, adminToken()).path("totalElements").asLong())
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void permissionsCatalog() {
        JsonNode perms = getJson("/permissions", adminToken());
        List<String> codes = new ArrayList<>();
        perms.forEach(p -> codes.add(p.path("code").asText()));
        assertThat(codes).contains("USER_MANAGE", "EXPERT_VIEW", "DOCUMENT_VERIFY", "SETTING_MANAGE");
    }
}
