package com.npcore.ems.expert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class ExpertIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    /** CERTIFICATION_MANAGER: EXPERT_CREATE/EDIT/VIEW:ALL, không có EXPERT_APPROVE/SUSPEND. */
    private TestUser manager;
    /** CERTIFICATION_DIRECTOR: EXPERT_APPROVE, EXPERT_SUSPEND. */
    private TestUser director;

    @BeforeEach
    void setUp() {
        manager = createUserWithRoles("CERTIFICATION_MANAGER");
        director = createUserWithRoles("CERTIFICATION_DIRECTOR");
    }

    private String expertId(JsonNode e) {
        return e.path("id").asText();
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private Map<String, Object> experience(String from, String to, boolean current, String verifiedUntil) {
        Map<String, Object> b = new HashMap<>();
        b.put("field", "Cơ khí");
        b.put("organization", "Công ty A");
        b.put("fromDate", from);
        b.put("toDate", to);
        b.put("isCurrent", current);
        b.put("verifiedUntil", verifiedUntil);
        return b;
    }

    private JsonNode certificate(String expertId, LocalDate expiry) {
        Map<String, Object> b = new HashMap<>();
        b.put("certificateName", "Lead Auditor ISO 9001");
        b.put("certificateNo", uniq("C"));
        b.put("issuedDate", expiry == null ? "2020-01-01" : expiry.minusYears(3).toString());
        b.put("expiryDate", expiry == null ? null : expiry.toString());
        return postJson("/experts/" + expertId + "/certificates", manager.token(), b, HttpStatus.CREATED);
    }

    @Test
    void createGeneratesCodeByEmploymentType() {
        JsonNode ft = createExpert(manager.token(), "Chuyên gia FT " + uniq(""), "FULLTIME", null);
        assertThat(ft.path("expertCode").asText()).matches("FT-\\d{4}");
        assertThat(ft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(ft.path("counts").path("experiences").asLong()).isZero();
        JsonNode pt = createExpert(manager.token(), "Chuyên gia PT " + uniq(""), "PARTTIME", null);
        assertThat(pt.path("expertCode").asText()).matches("PT-\\d{3}");

        JsonNode fetched = getJson("/experts/" + expertId(ft), manager.token());
        assertThat(fetched.path("expertCode").asText()).isEqualTo(ft.path("expertCode").asText());

        // validation
        Map<String, Object> bad = expertBody("x", "FREELANCE", null);
        expectError(post("/experts", manager.token(), bad), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        Map<String, Object> badId = expertBody("x", "FULLTIME", null);
        badId.put("idNumber", "123");
        expectError(post("/experts", manager.token(), badId), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        // quyền tạo
        expectError(post("/experts", director.token(), expertBody("x", "FULLTIME", null)), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(post("/experts", adminToken(), expertBody("x", "FULLTIME", null)), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void searchIgnoresVietnameseAccents() {
        String suffix = uniq("t");
        JsonNode e = createExpert(manager.token(), "Nguyễn Văn Đức " + suffix, "FULLTIME", null);
        JsonNode page = getJson("/experts?q=nguyen van duc " + suffix, manager.token());
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asText()).isEqualTo(expertId(e));

        JsonNode accented = getJson("/experts?q=NGUYỄN VĂN ĐỨC " + suffix, manager.token());
        assertThat(accented.path("totalElements").asLong()).isEqualTo(1);

        JsonNode broad = getJson("/experts?q=nguyen&size=1", manager.token());
        assertThat(broad.path("totalElements").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode byCode = getJson("/experts?q=" + e.path("expertCode").asText(), manager.token());
        assertThat(byCode.path("totalElements").asLong()).isEqualTo(1);

        JsonNode filtered = getJson("/experts?q=" + suffix + "&employmentType=PARTTIME", manager.token());
        assertThat(filtered.path("totalElements").asLong()).isZero();
    }

    @Test
    void statusWorkflowAndHistory() {
        JsonNode e = createExpert(manager.token(), "Workflow " + uniq(""), "FULLTIME", null);
        String id = expertId(e);
        String path = "/experts/" + id + "/status";

        // người không có EXPERT_APPROVE
        expectError(post(path, manager.token(), Map.of("action", "ACTIVATE")), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(texts(getJson("/experts/" + id, director.token()).path("availableActions"))).containsExactly("ACTIVATE");

        JsonNode active = postJson(path, director.token(), Map.of("action", "ACTIVATE"), HttpStatus.OK);
        assertThat(active.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(texts(active.path("availableActions"))).containsExactlyInAnyOrder("SUSPEND", "DEACTIVATE");

        expectError(post(path, director.token(), Map.of("action", "SUSPEND")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post(path, director.token(), Map.of("action", "SUSPEND", "comment", "  ")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        JsonNode suspended = postJson(path, director.token(), Map.of("action", "SUSPEND", "comment", "Vi phạm quy trình"), HttpStatus.OK);
        assertThat(suspended.path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(suspended.path("statusReason").asText()).isEqualTo("Vi phạm quy trình");

        expectError(post(path, director.token(), Map.of("action", "ACTIVATE")), HttpStatus.CONFLICT, "ILLEGAL_TRANSITION");
        expectError(post(path, director.token(), Map.of("action", "FLY")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        JsonNode reinstated = postJson(path, director.token(), Map.of("action", "REINSTATE", "comment", "Đã khắc phục"), HttpStatus.OK);
        assertThat(reinstated.path("status").asText()).isEqualTo("ACTIVE");

        JsonNode history = getJson("/experts/" + id + "/history", manager.token());
        List<String> actions = new ArrayList<>();
        history.forEach(h -> actions.add(h.path("action").asText()));
        assertThat(actions).contains("ACTIVATE", "SUSPEND", "REINSTATE", "CREATE");
        assertThat(actions.indexOf("REINSTATE")).isLessThan(actions.indexOf("SUSPEND"));     // mới nhất trước
        JsonNode suspendEntry = null;
        for (JsonNode h : history) if (h.path("action").asText().equals("SUSPEND")) suspendEntry = h;
        assertThat(suspendEntry).isNotNull();
        assertThat(suspendEntry.path("fromStatus").asText()).isEqualTo("ACTIVE");
        assertThat(suspendEntry.path("toStatus").asText()).isEqualTo("SUSPENDED");
        assertThat(suspendEntry.path("comment").asText()).isEqualTo("Vi phạm quy trình");
        assertThat(suspendEntry.path("actor").asText()).isEqualTo(director.username);
    }

    @Test
    void experienceYearsAreFrozen() {
        String id = expertId(createExpert(manager.token(), "Exp " + uniq(""), "FULLTIME", null));
        String base = "/experts/" + id + "/experiences";

        JsonNode finished = postJson(base, manager.token(), experience("2020-01-01", "2023-01-01", false, null), HttpStatus.CREATED);
        assertThat(finished.path("years").asDouble()).isCloseTo(3.0, within(0.05));
        assertThat(finished.path("isCurrent").asBoolean()).isFalse();
        assertThat(finished.path("verifiedUntil").isNull()).isTrue();

        LocalDate today = LocalDate.now();
        String from = today.minusYears(2).toString();
        JsonNode current = postJson(base, manager.token(), experience(from, null, true, null), HttpStatus.CREATED);
        assertThat(current.path("isCurrent").asBoolean()).isTrue();
        assertThat(current.path("verifiedUntil").asText()).isEqualTo(today.toString());
        assertThat(current.path("years").asDouble()).isCloseTo(2.0, within(0.05));

        // giả lập: kinh nghiệm được khai báo 1 năm trước → số năm tính tới mốc đó, không tự tăng tới hôm nay
        String itemId = current.path("id").asText();
        jdbc.update("update expert_experiences set verified_until = ? where experience_id = ?",
                today.minusYears(1), UUID.fromString(itemId));
        JsonNode listed = null;
        for (JsonNode x : getJson(base, manager.token())) if (x.path("id").asText().equals(itemId)) listed = x;
        assertThat(listed).isNotNull();
        assertThat(listed.path("years").asDouble()).isCloseTo(1.0, within(0.05));

        // sửa mô tả, không gửi verifiedUntil → giữ nguyên mốc cũ
        Map<String, Object> edit = experience(from, null, true, null);
        edit.put("description", "cập nhật");
        JsonNode edited = putJson(base + "/" + itemId, manager.token(), edit);
        assertThat(edited.path("verifiedUntil").asText()).isEqualTo(today.minusYears(1).toString());
        assertThat(edited.path("years").asDouble()).isCloseTo(1.0, within(0.05));

        // xác nhận lại tới hôm nay
        JsonNode reconfirmed = putJson(base + "/" + itemId, manager.token(), experience(from, null, true, today.toString()));
        assertThat(reconfirmed.path("years").asDouble()).isCloseTo(2.0, within(0.05));

        // quy tắc
        expectError(post(base, manager.token(), experience("2020-01-01", "2021-01-01", true, null)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post(base, manager.token(), experience("2020-01-01", null, false, null)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post(base, manager.token(), experience("2021-01-01", "2020-01-01", false, null)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post(base, manager.token(), experience(today.plusDays(5).toString(), null, true, null)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post(base, manager.token(), experience("2020-01-01", null, true, today.plusDays(1).toString())), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        assertThat(getJson("/experts/" + id, manager.token()).path("counts").path("experiences").asLong()).isEqualTo(2);
        expect(delete(base + "/" + finished.path("id").asText(), manager.token()), HttpStatus.NO_CONTENT);
        assertThat(getJson(base, manager.token())).hasSize(1);
    }

    @Test
    void certificateExpiryLevels() {
        String id = expertId(createExpert(manager.token(), "Cert " + uniq(""), "FULLTIME", null));
        LocalDate today = LocalDate.now();
        JsonNode high = certificate(id, today.plusDays(20));
        assertThat(high.path("expiryLevel").asText()).isEqualTo("HIGH");
        assertThat(high.path("daysToExpiry").asLong()).isEqualTo(20);
        assertThat(high.path("status").asText()).isEqualTo("VALID");
        assertThat(certificate(id, today.plusDays(5)).path("expiryLevel").asText()).isEqualTo("CRITICAL");
        assertThat(certificate(id, today.plusDays(7)).path("expiryLevel").asText()).isEqualTo("CRITICAL");
        assertThat(certificate(id, today.plusDays(45)).path("expiryLevel").asText()).isEqualTo("WARNING");
        assertThat(certificate(id, today.plusDays(200)).path("expiryLevel").asText()).isEqualTo("NONE");
        JsonNode expired = certificate(id, today.minusDays(1));
        assertThat(expired.path("expiryLevel").asText()).isEqualTo("EXPIRED");
        assertThat(expired.path("daysToExpiry").asLong()).isEqualTo(-1);
        JsonNode noExpiry = certificate(id, null);
        assertThat(noExpiry.path("expiryLevel").asText()).isEqualTo("NONE");
        assertThat(noExpiry.path("daysToExpiry").isNull()).isTrue();

        assertThat(getJson("/experts/" + id + "/certificates", manager.token())).hasSize(7);
        expectError(post("/experts/" + id + "/certificates", manager.token(), Map.of("certificateName", "x",
                "issuedDate", "2024-01-01", "expiryDate", "2023-01-01")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void languagesUpsertAndDelete() {
        String id = expertId(createExpert(manager.token(), "Lang " + uniq(""), "FULLTIME", null));
        String base = "/experts/" + id + "/languages";
        JsonNode en = putJson(base + "/EN", manager.token(), Map.of("proficiency", "FLUENT", "canAudit", true));
        assertThat(en.path("language").asText()).isEqualTo("en");
        assertThat(en.path("canAudit").asBoolean()).isTrue();
        JsonNode en2 = putJson(base + "/en", manager.token(), Map.of("proficiency", "NATIVE", "canAudit", false));
        assertThat(en2.path("proficiency").asText()).isEqualTo("NATIVE");
        putJson(base + "/vi", manager.token(), Map.of("proficiency", "NATIVE", "canAudit", true));
        JsonNode list = getJson(base, manager.token());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("language").asText()).isEqualTo("en");
        assertThat(list.get(0).path("proficiency").asText()).isEqualTo("NATIVE");

        expectError(put(base + "/eng", manager.token(), Map.of("proficiency", "BASIC")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(put(base + "/fr", manager.token(), Map.of("proficiency", "GOOD")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        expect(delete(base + "/EN", manager.token()), HttpStatus.NO_CONTENT);
        expectError(delete(base + "/en", manager.token()), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(getJson(base, manager.token())).hasSize(1);
    }

    @Test
    void expertRoleSeesAndEditsOnlyOwnProfile() {
        TestUser expertUser = createUserWithRoles("EXPERT");
        JsonNode own = createExpert(manager.token(), "Chuyên gia tự phục vụ " + uniq(""), "FULLTIME", expertUser.id);
        String ownId = expertId(own);
        assertThat(own.path("username").asText()).isEqualTo(expertUser.username);
        JsonNode other = createExpert(manager.token(), "Người khác " + uniq(""), "PARTTIME", null);
        String otherId = expertId(other);

        // không gắn 1 tài khoản cho 2 chuyên gia
        expectError(post("/experts", manager.token(), expertBody("dup", "FULLTIME", expertUser.id)), HttpStatus.CONFLICT, "DUPLICATE");

        String token = expertUser.token();
        assertThat(getJson("/auth/me", token).path("expertId").asText()).isEqualTo(ownId);
        JsonNode me = getJson("/experts/me", token);
        assertThat(me.path("id").asText()).isEqualTo(ownId);
        assertThat(me.path("availableActions")).isEmpty();
        expect(get("/experts/" + ownId, token), HttpStatus.OK);

        // tự sửa: phone đổi được, employmentType / expertType bị bỏ qua
        Map<String, Object> edit = expertBody(own.path("fullName").asText(), "PARTTIME", null);
        edit.put("phone", "0987654321");
        edit.put("expertType", "BOTH");
        JsonNode edited = putJson("/experts/" + ownId, token, edit);
        assertThat(edited.path("phone").asText()).isEqualTo("0987654321");
        assertThat(edited.path("employmentType").asText()).isEqualTo("FULLTIME");
        assertThat(edited.path("expertType").asText()).isEqualTo("AUDITOR");
        assertThat(edited.path("expertCode").asText()).isEqualTo(own.path("expertCode").asText());
        assertThat(edited.path("userId").asText()).isEqualTo(expertUser.id.toString());

        // danh sách và hồ sơ người khác
        expectError(get("/experts", token), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(get("/experts/" + otherId, token), HttpStatus.NOT_FOUND, "NOT_FOUND");
        expectError(put("/experts/" + otherId, token, expertBody("hack", "FULLTIME", null)), HttpStatus.NOT_FOUND, "NOT_FOUND");
        expectError(get("/experts/" + otherId + "/certificates", token), HttpStatus.NOT_FOUND, "NOT_FOUND");
        expectError(post("/experts/" + otherId + "/experiences", token, experience("2020-01-01", "2021-01-01", false, null)),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
        expectError(post("/experts", token, expertBody("x", "FULLTIME", null)), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(post("/experts/" + ownId + "/status", token, Map.of("action", "ACTIVATE")), HttpStatus.FORBIDDEN, "FORBIDDEN");

        // hồ sơ con của chính mình
        postJson("/experts/" + ownId + "/experiences", token, experience("2019-01-01", "2020-01-01", false, null), HttpStatus.CREATED);
        putJson("/experts/" + ownId + "/languages/vi", token, Map.of("proficiency", "NATIVE", "canAudit", true));
        assertThat(getJson("/experts/" + ownId + "/experiences", token)).hasSize(1);

        // user không gắn chuyên gia
        TestUser lonely = createUserWithRoles("EXPERT");
        expectError(get("/experts/me", lonely.token()), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void csvImportReportsRowErrors() {
        String tag = uniq("imp");
        String code = ("IMP-" + tag).toUpperCase();
        String csv = String.join(",", ExpertImportService.COLUMNS) + "\n"
                + code + ",Lê Văn Một " + tag + ",1980-05-20,Nam,001080000001,Hà Nội,0912345678," + tag + "1@x.vn,CGĐG,FULLTIME,,Chuyên gia,2015-03-01\n"
                + ",Trần Thị Hai " + tag + ",12/08/1985,FEMALE,,Hải Phòng,,," + "CGKT,Parttime,,,\n"
                + ",Phạm Văn Ba " + tag + ",1985-13-45,MALE,,,,,AUDITOR,FULLTIME,,,\n";
        JsonNode result = expect(uploadMultipart("/experts/import", manager.token(), "experts.csv", csv), HttpStatus.OK);
        assertThat(result.path("total").asInt()).isEqualTo(3);
        assertThat(result.path("imported").asInt()).isEqualTo(2);
        assertThat(result.path("errors")).hasSize(1);
        assertThat(result.path("errors").get(0).path("row").asInt()).isEqualTo(4);
        assertThat(result.path("errors").get(0).path("message").asText()).contains("date_of_birth");

        JsonNode page = getJson("/experts?q=" + tag + "&sort=fullName,asc", manager.token());
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        Map<String, JsonNode> byName = new HashMap<>();
        page.path("content").forEach(n -> byName.put(n.path("fullName").asText(), n));
        JsonNode one = byName.get("Lê Văn Một " + tag);
        JsonNode two = byName.get("Trần Thị Hai " + tag);
        assertThat(one.path("expertCode").asText()).isEqualTo(code);
        assertThat(one.path("expertType").asText()).isEqualTo("AUDITOR");
        assertThat(two.path("expertCode").asText()).matches("PT-\\d{3}");
        assertThat(two.path("expertType").asText()).isEqualTo("TECHNICAL_EXPERT");
        JsonNode twoDetail = getJson("/experts/" + two.path("id").asText(), manager.token());
        assertThat(twoDetail.path("dateOfBirth").asText()).isEqualTo("1985-08-12");
        assertThat(twoDetail.path("gender").asText()).isEqualTo("FEMALE");

        // mã quá dài (cột VARCHAR(30)) chỉ làm hỏng dòng đó, các dòng khác vẫn nhập
        String tag2 = uniq("imq");
        String longCode = "FT-" + "9".repeat(40);
        String csv2 = String.join(",", ExpertImportService.COLUMNS) + "\n"
                + longCode + ",Mã Dài " + tag2 + ",,,,,,,AUDITOR,FULLTIME,,,\n"
                + ",Hợp Lệ " + tag2 + ",,,,,,,AUDITOR,FULLTIME,,,\n";
        JsonNode result2 = expect(uploadMultipart("/experts/import", manager.token(), "experts2.csv", csv2), HttpStatus.OK);
        assertThat(result2.path("imported").asInt()).isEqualTo(1);
        assertThat(result2.path("errors")).hasSize(1);
        assertThat(result2.path("errors").get(0).path("row").asInt()).isEqualTo(2);
        assertThat(getJson("/experts?q=" + tag2, manager.token()).path("totalElements").asLong()).isEqualTo(1);

        // import lại cùng file: dòng có mã cũ bị báo trùng
        JsonNode again = expect(uploadMultipart("/experts/import", manager.token(), "experts.csv", csv), HttpStatus.OK);
        assertThat(again.path("errors").get(0).path("row").asInt()).isEqualTo(2);

        // template và quyền
        var template = get("/experts/import/template", manager.token());
        assertThat(template.getStatusCode().value()).isEqualTo(200);
        assertThat(template.getBody()).contains(String.join(",", ExpertImportService.COLUMNS));
        expectError(uploadMultipart("/experts/import", director.token(), "experts.csv", csv), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
}
