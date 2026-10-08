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

    @Autowired
    SuspensionExpiryJob suspensionJob;

    /** CERTIFICATION_MANAGER: EXPERT_CREATE/EDIT/VIEW:ALL, không có EXPERT_APPROVE/SUSPEND. */
    private TestUser manager;
    /** CERTIFICATION_DIRECTOR: EXPERT_APPROVE, EXPERT_SUSPEND. */
    private TestUser director;
    /** TECHNICAL_REVIEWER = Chuyên gia trưởng: EXPERT_REVIEW. */
    private TestUser reviewer;

    @BeforeEach
    void setUp() {
        manager = createUserWithRoles("CERTIFICATION_MANAGER");
        director = createUserWithRoles("CERTIFICATION_DIRECTOR");
        reviewer = createUserWithRoles("TECHNICAL_REVIEWER");
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

    /** Đủ điều kiện nộp: ngày sinh, SĐT (expertBody có sẵn), 1 học vấn, 1 kinh nghiệm. */
    private void completeProfile(String id, String token) {
        Map<String, Object> body = expertBody(getJson("/experts/" + id, token).path("fullName").asText(), "FULLTIME", null);
        body.put("dateOfBirth", "1985-05-20");
        putJson("/experts/" + id, token, body);
        postJson("/experts/" + id + "/educations", token, Map.of("degreeLevelCode", "ENGINEER", "institution", "ĐH Bách khoa"),
                HttpStatus.CREATED);
        postJson("/experts/" + id + "/experiences", token, experience("2015-01-01", "2020-01-01", false, null), HttpStatus.CREATED);
    }

    @Test
    void statusWorkflowAndHistory() {
        JsonNode e = createExpert(manager.token(), "Workflow " + uniq(""), "FULLTIME", null);
        String id = expertId(e);
        String path = "/experts/" + id + "/status";

        // Nháp: không phê duyệt thẳng; trình khi hồ sơ còn thiếu bị chặn
        expectError(post(path, director.token(), Map.of("action", "APPROVE")), HttpStatus.CONFLICT, "ILLEGAL_TRANSITION");
        assertThat(texts(getJson("/experts/" + id, manager.token()).path("availableActions"))).containsExactly("SUBMIT");
        expectError(post(path, manager.token(), Map.of("action", "SUBMIT")), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");

        // NV hồ sơ trình → GĐCN trả lại (bắt buộc nội dung) → sửa, trình lại → GĐCN phê duyệt
        completeProfile(id, manager.token());
        JsonNode submitted = postJson(path, manager.token(), Map.of("action", "SUBMIT"), HttpStatus.OK);
        assertThat(submitted.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(texts(submitted.path("availableActions"))).isEmpty();                      // NV hồ sơ không duyệt
        expectError(put("/experts/" + id, manager.token(), expertBody("Sửa khi chờ", "FULLTIME", null)),
                HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");                            // khoá khi chờ duyệt
        // Chưa thẩm tra thì GĐCN chưa phê duyệt được
        expectError(post(path, director.token(), Map.of("action", "APPROVE")), HttpStatus.CONFLICT, "ILLEGAL_TRANSITION");
        assertThat(texts(getJson("/experts/" + id, director.token()).path("availableActions"))).isEmpty();
        // Chuyên gia trưởng thẩm tra: trả lại (bắt buộc ghi chú)
        expectError(post(path, manager.token(), Map.of("action", "REVIEW")), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(texts(getJson("/experts/" + id, reviewer.token()).path("availableActions")))
                .containsExactlyInAnyOrder("REVIEW", "RETURN");
        expectError(post(path, reviewer.token(), Map.of("action", "RETURN")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        JsonNode returned = postJson(path, reviewer.token(), Map.of("action", "RETURN", "comment", "Bổ sung bằng cấp"), HttpStatus.OK);
        assertThat(returned.path("status").asText()).isEqualTo("DRAFT");
        assertThat(returned.path("statusReason").asText()).isEqualTo("Bổ sung bằng cấp");
        Map<String, Object> fix = expertBody(e.path("fullName").asText(), "FULLTIME", null);
        fix.put("dateOfBirth", "1985-05-20");
        fix.put("address", "Bổ sung theo yêu cầu GĐCN");
        putJson("/experts/" + id, manager.token(), fix);
        postJson(path, manager.token(), Map.of("action", "SUBMIT"), HttpStatus.OK);
        // thẩm tra đạt kèm ghi chú → chờ GĐCN; vẫn khoá sửa
        JsonNode reviewed = postJson(path, reviewer.token(), Map.of("action", "REVIEW", "comment", "Đủ năng lực code 17, 18"), HttpStatus.OK);
        assertThat(reviewed.path("status").asText()).isEqualTo("REVIEWED");
        assertThat(reviewed.path("statusReason").asText()).isEqualTo("Đủ năng lực code 17, 18");
        expectError(post("/experts/" + id + "/educations", manager.token(), Map.of("degreeLevelCode", "MASTER", "institution", "X")),
                HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
        expectError(post(path, reviewer.token(), Map.of("action", "APPROVE")), HttpStatus.FORBIDDEN, "FORBIDDEN");   // CG trưởng không phê duyệt
        assertThat(texts(getJson("/experts/" + id, director.token()).path("availableActions")))
                .containsExactlyInAnyOrder("APPROVE", "RETURN");
        JsonNode active = postJson(path, director.token(), Map.of("action", "APPROVE"), HttpStatus.OK);
        assertThat(active.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(texts(active.path("availableActions"))).containsExactlyInAnyOrder("SUSPEND", "DEACTIVATE");

        expectError(post(path, director.token(), Map.of("action", "SUSPEND")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post(path, director.token(), Map.of("action", "SUSPEND", "comment", "  ")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        JsonNode suspended = postJson(path, director.token(), Map.of("action", "SUSPEND", "comment", "Vi phạm quy trình"), HttpStatus.OK);
        assertThat(suspended.path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(suspended.path("statusReason").asText()).isEqualTo("Vi phạm quy trình");

        expectError(post(path, director.token(), Map.of("action", "APPROVE")), HttpStatus.CONFLICT, "ILLEGAL_TRANSITION");
        expectError(post(path, director.token(), Map.of("action", "FLY")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        JsonNode reinstated = postJson(path, director.token(), Map.of("action", "REINSTATE", "comment", "Đã khắc phục"), HttpStatus.OK);
        assertThat(reinstated.path("status").asText()).isEqualTo("ACTIVE");

        JsonNode history = getJson("/experts/" + id + "/history", manager.token());
        List<String> actions = new ArrayList<>();
        history.forEach(h -> actions.add(h.path("action").asText()));
        assertThat(actions).contains("SUBMIT", "RETURN", "REVIEW", "APPROVE", "SUSPEND", "REINSTATE", "CREATE");
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

        // tự sửa: chỉ thông tin liên hệ; họ tên, ngày sinh, loại, hợp đồng... bị bỏ qua
        Map<String, Object> edit = expertBody("Đổi tên trái phép", "PARTTIME", null);
        edit.put("phone", "0987654321");
        edit.put("address", "12 Láng Hạ, Hà Nội");
        edit.put("dateOfBirth", "1999-01-01");
        edit.put("expertType", "BOTH");
        JsonNode edited = putJson("/experts/" + ownId, token, edit);
        assertThat(edited.path("phone").asText()).isEqualTo("0987654321");
        assertThat(edited.path("address").asText()).isEqualTo("12 Láng Hạ, Hà Nội");
        assertThat(edited.path("fullName").asText()).isEqualTo(own.path("fullName").asText());
        assertThat(edited.path("dateOfBirth").isNull()).isTrue();
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
        expectError(post("/experts/" + ownId + "/status", token, Map.of("action", "SUBMIT")), HttpStatus.FORBIDDEN, "FORBIDDEN");

        // hồ sơ con của chính mình
        // năng lực của chính mình: chỉ xem, không tự thêm / sửa (NV hồ sơ làm)
        expectError(post("/experts/" + ownId + "/experiences", token, experience("2019-01-01", "2020-01-01", false, null)),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(put("/experts/" + ownId + "/languages/vi", token, Map.of("proficiency", "NATIVE", "canAudit", true)),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        postJson("/experts/" + ownId + "/experiences", manager.token(), experience("2019-01-01", "2020-01-01", false, null), HttpStatus.CREATED);
        assertThat(getJson("/experts/" + ownId + "/experiences", token)).hasSize(1);

        // user không gắn chuyên gia
        TestUser lonely = createUserWithRoles("EXPERT");
        expectError(get("/experts/me", lonely.token()), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void submitterCannotApproveOwnSubmission() {
        // một người kiêm nhiều vai trò: trình được nhưng không tự thẩm tra / tự phê duyệt hồ sơ mình trình (SoD)
        TestUser all = createUserWithRoles("CERTIFICATION_MANAGER", "TECHNICAL_REVIEWER", "CERTIFICATION_DIRECTOR");
        JsonNode e = createExpert(all.token(), "SoD " + uniq(""), "FULLTIME", null);
        completeProfile(expertId(e), all.token());
        String path = "/experts/" + expertId(e) + "/status";
        postJson(path, all.token(), Map.of("action", "SUBMIT"), HttpStatus.OK);
        expectError(post(path, all.token(), Map.of("action", "REVIEW")), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
        postJson(path, reviewer.token(), Map.of("action", "REVIEW"), HttpStatus.OK);
        expectError(post(path, all.token(), Map.of("action", "APPROVE")), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
        postJson(path, director.token(), Map.of("action", "APPROVE"), HttpStatus.OK);
    }

    @Test
    void permissionsFollowVinaCertRoles() {
        TestUser office = createUserWithRoles("DOCUMENT_CONTROLLER");          // Văn phòng
        TestUser head = createUserWithRoles("HEAD_CERTIFICATION");             // Trưởng / phó phòng CN

        // Văn phòng: nhập thông tin nhân sự, không nhập năng lực, không trình
        JsonNode e = createExpert(office.token(), "Nhân sự mới " + uniq(""), "FULLTIME", null);
        String id = expertId(e);
        Map<String, Object> hr = expertBody(e.path("fullName").asText(), "FULLTIME", null);
        hr.put("dateOfBirth", "1990-02-02");
        hr.put("position", "Chuyên gia đánh giá");
        assertThat(putJson("/experts/" + id, office.token(), hr).path("position").asText()).isEqualTo("Chuyên gia đánh giá");
        expectError(post("/experts/" + id + "/experiences", office.token(), experience("2015-01-01", "2016-01-01", false, null)),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(texts(getJson("/experts/" + id, office.token()).path("availableActions"))).isEmpty();
        expectError(post("/experts/" + id + "/status", office.token(), Map.of("action", "SUBMIT")), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(uploadMultipart("/experts/import", office.token(), "ho-so.csv",
                "expertCode,fullName\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), null), HttpStatus.FORBIDDEN, "FORBIDDEN");

        // NV hồ sơ: nhập năng lực rồi trình
        completeProfile(id, manager.token());
        postJson("/experts/" + id + "/status", manager.token(), Map.of("action", "SUBMIT"), HttpStatus.OK);
        postJson("/experts/" + id + "/status", reviewer.token(), Map.of("action", "REVIEW"), HttpStatus.OK);

        // GĐCN: chỉ duyệt, không sửa nội dung
        expectError(put("/experts/" + id, director.token(), hr), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(post("/experts/" + id + "/educations", director.token(), Map.of("degreeLevelCode", "MASTER", "institution", "X")),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        postJson("/experts/" + id + "/status", director.token(), Map.of("action", "APPROVE"), HttpStatus.OK);

        // Trưởng / phó phòng: xem toàn bộ, không sửa, không duyệt
        assertThat(getJson("/experts?q=" + e.path("expertCode").asText(), head.token()).path("totalElements").asLong()).isEqualTo(1);
        assertThat(getJson("/experts/" + id + "/experiences", head.token())).hasSize(1);
        assertThat(texts(getJson("/experts/" + id, head.token()).path("availableActions"))).isEmpty();
        expectError(put("/experts/" + id, head.token(), hr), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(post("/experts", head.token(), expertBody("x", "FULLTIME", null)), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void timedSuspensionReopensAutomatically() {
        JsonNode e = createExpert(manager.token(), "Dừng có hạn " + uniq(""), "FULLTIME", null);
        String id = expertId(e);
        String path = "/experts/" + id + "/status";
        completeProfile(id, manager.token());
        postJson(path, manager.token(), Map.of("action", "SUBMIT"), HttpStatus.OK);
        postJson(path, reviewer.token(), Map.of("action", "REVIEW"), HttpStatus.OK);
        postJson(path, director.token(), Map.of("action", "APPROVE"), HttpStatus.OK);

        String until = LocalDate.now().plusDays(30).toString();
        expectError(post(path, director.token(), Map.of("action", "SUSPEND", "comment", "x", "suspendedUntil",
                LocalDate.now().toString())), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        JsonNode suspended = postJson(path, director.token(),
                Map.of("action", "SUSPEND", "comment", "Chờ witness", "suspendedUntil", until), HttpStatus.OK);
        assertThat(suspended.path("suspendedUntil").asText()).isEqualTo(until);
        assertThat(suspended.path("statusReason").asText()).startsWith("Chờ witness (dừng đến hết");

        // chưa hết hạn: job không mở
        suspensionJob.run();
        assertThat(getJson("/experts/" + id, manager.token()).path("status").asText()).isEqualTo("SUSPENDED");

        // giả lập đã quá hạn
        jdbc.update("UPDATE experts SET suspended_until = current_date - 1 WHERE expert_id = ?::uuid", id);
        assertThat(suspensionJob.run()).isGreaterThanOrEqualTo(1);
        JsonNode reopened = getJson("/experts/" + id, manager.token());
        assertThat(reopened.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(reopened.path("suspendedUntil").isNull()).isTrue();
        JsonNode last = getJson("/experts/" + id + "/history", manager.token()).get(0);
        assertThat(last.path("action").asText()).isEqualTo("REINSTATE");
        assertThat(last.path("actor").asText()).isEqualTo(director.username);
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

    @Test
    void importCanCreateExpertAccountsWithTemporaryPassword() {
        String tag = uniq("acc");
        String cols = String.join(",", ExpertImportService.COLUMNS) + "\n";
        String csv = cols
                + ",Ngô Tài Khoản " + tag + ",,,,,,Tk." + tag + "@x.vn,CGĐG,FULLTIME,,,,\n"          // tự sinh từ email
                + ",Bùi Đặt Tên " + tag + ",,,,,,b" + tag + "@x.vn,CGKT,PARTTIME,,,,u" + tag + "\n"  // username tự đặt
                + ",Không Email " + tag + ",,,,,,,CGKT,PARTTIME,,,,\n"                                  // cảnh báo, vẫn nhập
                + ",Trùng Tên " + tag + ",,,,,,c" + tag + "@x.vn,CGKT,PARTTIME,,,,u" + tag + "\n";     // username lặp
        JsonNode r = expect(uploadMultipart("/experts/import", manager.token(), "acc.csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), Map.of("createAccounts", true)), HttpStatus.OK);
        assertThat(r.path("imported").asInt()).isEqualTo(4);
        assertThat(r.path("errors")).isEmpty();
        assertThat(r.path("accounts")).hasSize(2);
        assertThat(r.path("warnings")).hasSize(2);
        assertThat(r.path("warnings").get(0).path("row").asInt()).isEqualTo(4);
        assertThat(r.path("warnings").get(1).path("message").asText()).contains("lặp");

        JsonNode auto = r.path("accounts").get(0);
        assertThat(auto.path("username").asText()).isEqualTo("tk." + tag);
        String temp = auto.path("tempPassword").asText();
        assertThat(temp).hasSize(10).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*\\d.*");

        // đăng nhập bằng mật khẩu tạm: bị yêu cầu đổi, chỉ thấy hồ sơ của mình
        JsonNode login = loginJson("tk." + tag, temp);
        assertThat(login.path("user").path("mustChangePassword").asBoolean()).isTrue();
        assertThat(login.path("user").path("roles").get(0).asText()).isEqualTo("EXPERT");
        assertThat(login.path("user").path("expertId").asText()).isNotBlank();
        String token = login.path("accessToken").asText();
        expect(post("/auth/change-password", token, Map.of("currentPassword", temp, "newPassword", temp)),
                HttpStatus.BAD_REQUEST);
        expect(post("/auth/change-password", token, Map.of("currentPassword", temp, "newPassword", "M0i!matkhau")),
                HttpStatus.NO_CONTENT);
        assertThat(loginJson("tk." + tag, "M0i!matkhau").path("user").path("mustChangePassword").asBoolean()).isFalse();

        assertThat(r.path("accounts").get(1).path("username").asText()).isEqualTo("u" + tag);

        // import lại cùng email: không tạo tài khoản thứ hai
        JsonNode again = expect(uploadMultipart("/experts/import", manager.token(), "acc2.csv",
                (cols + ",Lặp Lại " + tag + ",,,,,,tk." + tag + "@x.vn,CGĐG,FULLTIME,,,,\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8), Map.of("createAccounts", true)), HttpStatus.OK);
        assertThat(again.path("imported").asInt()).isEqualTo(1);
        assertThat(again.path("accounts")).isEmpty();
        assertThat(again.path("warnings").get(0).path("message").asText()).contains("đã có tài khoản");
    }
}
