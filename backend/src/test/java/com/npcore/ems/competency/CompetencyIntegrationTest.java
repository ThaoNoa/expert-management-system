package com.npcore.ems.competency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class CompetencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private TestUser manager;
    private TestUser reviewer;
    private TestUser director;

    @BeforeEach
    void setUp() {
        manager = createUserWithRoles("CERTIFICATION_MANAGER");
        reviewer = createUserWithRoles("TECHNICAL_REVIEWER");
        director = createUserWithRoles("CERTIFICATION_DIRECTOR");
    }

    @Test
    void shouldCreateDefinitionAndRegisterCompetencyForExpert() {
        // 1. Tạo Scheme & Standard phục vụ test
        UUID schemeId = jdbc.queryForObject(
                "INSERT INTO schemes (scheme_code, scheme_name) VALUES (?, ?) RETURNING scheme_id",
                UUID.class, "SCH-" + uniq("S"), "Scheme Test");
        UUID standardId = jdbc.queryForObject(
                "INSERT INTO standards (standard_code, standard_name, scheme_id) VALUES (?, ?, ?) RETURNING standard_id",
                UUID.class, "STD-" + uniq("T"), "Standard Test", schemeId);
        UUID roleId = jdbc.queryForObject(
                "SELECT assessment_role_id FROM assessment_roles WHERE role_code = 'LA' LIMIT 1", UUID.class);

        // 2. Tạo Competency Definition bằng SUPER_ADMIN
        Map<String, Object> defReq = new HashMap<>();
        defReq.put("schemeId", schemeId);
        defReq.put("standardId", standardId);
        defReq.put("assessmentRoleId", roleId);
        defReq.put("defaultValidityMonths", 36);
        defReq.put("effectiveFrom", LocalDate.now().toString());
        defReq.put("version", "TEST-" + uniq("V"));
        defReq.put("status", "ACTIVE");

        JsonNode defBody = postJson("/competency-definitions", adminToken(), defReq, HttpStatus.CREATED);
        String defId = defBody.path("id").asText();
        assertThat(defId).isNotBlank();

        // 3. Tạo một Expert mới bằng CERTIFICATION_MANAGER
        Map<String, Object> expReq = new HashMap<>();
        expReq.put("fullName", "Chuyên Gia Test " + uniq("E"));
        expReq.put("expertType", "AUDITOR");
        expReq.put("employmentType", "FULLTIME");
        expReq.put("email", uniq("test") + "@ems.local");

        JsonNode expBody = postJson("/experts", manager.token(), expReq, HttpStatus.CREATED);
        String expertId = expBody.path("id").asText();

        // 4. Đăng ký Competency cho Expert
        Map<String, Object> compReq = new HashMap<>();
        compReq.put("competencyDefinitionId", defId);
        compReq.put("competencyLevel", "QUALIFIED");
        compReq.put("notes", "Đăng ký năng lực Lead Auditor");

        JsonNode compBody = postJson("/experts/" + expertId + "/competencies", manager.token(), compReq, HttpStatus.CREATED);
        String compId = compBody.path("id").asText();
        assertThat(compBody.path("status").asText()).isEqualTo("DRAFT");

        // 5. Thêm Evidence
        Map<String, Object> evReq = new HashMap<>();
        evReq.put("evidenceType", "EDUCATION");
        evReq.put("description", "Bằng đại học ngành kỹ thuật");
        postJson("/experts/" + expertId + "/competencies/" + compId + "/evidences", manager.token(), evReq, HttpStatus.CREATED);

        // 6. Workflow: SUBMIT
        Map<String, Object> submitReq = new HashMap<>();
        submitReq.put("action", "SUBMIT");
        JsonNode submitBody = postJson("/experts/" + expertId + "/competencies/" + compId + "/actions", manager.token(), submitReq, HttpStatus.OK);
        assertThat(submitBody.path("status").asText()).isEqualTo("SUBMITTED");

        // 7. Workflow: START_REVIEW (bởi reviewer - TECHNICAL_REVIEWER)
        Map<String, Object> startReviewReq = new HashMap<>();
        startReviewReq.put("action", "START_REVIEW");
        JsonNode reviewBody = postJson("/experts/" + expertId + "/competencies/" + compId + "/actions", reviewer.token(), startReviewReq, HttpStatus.OK);
        assertThat(reviewBody.path("status").asText()).isEqualTo("UNDER_REVIEW");

        // 8. Workflow: APPROVE (bởi director - CERTIFICATION_DIRECTOR có quyền COMPETENCY_APPROVE)
        Map<String, Object> approveReq = new HashMap<>();
        approveReq.put("action", "APPROVE");
        JsonNode approvedBody = postJson("/experts/" + expertId + "/competencies/" + compId + "/actions", director.token(), approveReq, HttpStatus.OK);
        assertThat(approvedBody.path("status").asText()).isEqualTo("APPROVED");
        assertThat(approvedBody.path("effectiveFrom").asText()).isEqualTo(LocalDate.now().toString());

        // 9. Tra cứu ma trận Competency Matrix
        JsonNode matrixBody = getJson("/competency-matrix?standardId=" + standardId, manager.token());
        assertThat(matrixBody.path("rows").size()).isGreaterThanOrEqualTo(1);
        assertThat(matrixBody.path("columns").get(0).path("codeValue").asText()).isEqualTo("*");
    }

    /** Scheme + tiêu chuẩn + bộ mã (17, 17.1 con của 17, 18) đã kích hoạt; trả về [schemeId, standardId, codeSetId]. */
    private UUID[] schemeWithCodes(boolean parentCoversChild) {
        UUID schemeId = jdbc.queryForObject(
                "INSERT INTO schemes (scheme_code, scheme_name, parent_covers_child) VALUES (?, ?, ?) RETURNING scheme_id",
                UUID.class, "SC-" + uniq("S"), "Scheme", parentCoversChild);
        UUID standardId = jdbc.queryForObject(
                "INSERT INTO standards (standard_code, standard_name, scheme_id) VALUES (?, ?, ?) RETURNING standard_id",
                UUID.class, "ST-" + uniq("T"), "Standard", schemeId);
        JsonNode set = postJson("/code-sets", adminToken(), Map.of("schemeId", schemeId, "version", "V1",
                "effectiveFrom", "2024-01-01"), HttpStatus.CREATED);
        String setId = set.path("id").asText();
        postJson("/code-sets/" + setId + "/codes", adminToken(), Map.of("codeValue", "17", "codeName", "Kim loại"), HttpStatus.CREATED);
        postJson("/code-sets/" + setId + "/codes", adminToken(), Map.of("codeValue", "17.1", "codeName", "Kim loại cơ bản",
                "parentCode", "17"), HttpStatus.CREATED);
        postJson("/code-sets/" + setId + "/codes", adminToken(), Map.of("codeValue", "18", "codeName", "Máy móc"), HttpStatus.CREATED);
        postJson("/code-sets/" + setId + "/activate", adminToken(), null, HttpStatus.OK);
        return new UUID[] {schemeId, standardId, UUID.fromString(setId)};
    }

    private UUID codeId(UUID setId, String value) {
        return jdbc.queryForObject("SELECT code_id FROM codes WHERE code_set_id = ? AND code_value = ?", UUID.class, setId, value);
    }

    private UUID roleId(String code) {
        return jdbc.queryForObject("SELECT assessment_role_id FROM assessment_roles WHERE role_code = ?", UUID.class, code);
    }

    private Map<String, Object> def(UUID schemeId, UUID standardId, UUID codeId, UUID roleId) {
        Map<String, Object> m = new HashMap<>();
        m.put("schemeId", schemeId);
        m.put("standardId", standardId);
        m.put("codeId", codeId);
        m.put("assessmentRoleId", roleId);
        m.put("defaultValidityMonths", 36);
        m.put("effectiveFrom", "2024-01-01");
        m.put("version", "1");
        return m;
    }

    @Test
    void definitionsAreValidatedAndCanBeCreatedInBulk() {
        UUID[] a = schemeWithCodes(false);
        UUID[] other = schemeWithCodes(false);
        UUID la = roleId("LA");
        UUID au = roleId("AU");

        // toàn tiêu chuẩn: lần 2 trùng → 409 (trước đây lỗi 500)
        postJson("/competency-definitions", adminToken(), def(a[0], a[1], null, la), HttpStatus.CREATED);
        expectError(post("/competency-definitions", adminToken(), def(a[0], a[1], null, la)), HttpStatus.CONFLICT, "DUPLICATE");
        // scheme không khớp tiêu chuẩn; code của scheme khác
        expectError(post("/competency-definitions", adminToken(), def(other[0], a[1], null, au)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post("/competency-definitions", adminToken(), def(a[0], a[1], codeId(other[2], "17"), au)),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        // theo code + tiêu chí
        Map<String, Object> withCode = def(a[0], a[1], codeId(a[2], "17"), au);
        withCode.put("criteria", Map.of("minYearsExperience", 4, "minAudits", 3));
        JsonNode d17 = postJson("/competency-definitions", adminToken(), withCode, HttpStatus.CREATED);
        assertThat(d17.path("codeValue").asText()).isEqualTo("17");
        assertThat(d17.path("criteria").path("minYearsExperience").asInt()).isEqualTo(4);
        // sửa không làm mất code
        JsonNode edited = putJson("/competency-definitions/" + d17.path("id").asText(), adminToken(), withCode);
        assertThat(edited.path("codeValue").asText()).isEqualTo("17");

        // hàng loạt: AU cho 17 (đã có → bỏ qua), 17.1, 18 + toàn tiêu chuẩn
        Map<String, Object> bulk = new HashMap<>();
        bulk.put("standardId", a[1]);
        bulk.put("assessmentRoleId", au);
        bulk.put("codeIds", List.of(codeId(a[2], "17"), codeId(a[2], "17.1"), codeId(a[2], "18")));
        bulk.put("includeGeneral", true);
        bulk.put("effectiveFrom", "2024-01-01");
        JsonNode r = postJson("/competency-definitions/bulk", adminToken(), bulk, HttpStatus.OK);
        assertThat(r.path("created").asInt()).isEqualTo(3);
        assertThat(r.path("skipped").asInt()).isEqualTo(1);
        JsonNode list = getJson("/competency-definitions?standardId=" + a[1] + "&roleId=" + au + "&size=50", manager.token());
        assertThat(list.path("totalElements").asLong()).isEqualTo(4);
        assertThat(list.path("content").get(0).path("codeValue").isNull()).isTrue();          // toàn tiêu chuẩn đứng đầu
        assertThat(getJson("/competency-definitions?standardId=" + a[1] + "&q=17.1", manager.token())
                .path("totalElements").asLong()).isEqualTo(1);
        TestUser office = createUserWithRoles("DOCUMENT_CONTROLLER");          // không có quyền danh mục
        expectError(post("/competency-definitions/bulk", office.token(), bulk), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void registrationUsesCatalogAndMatrixShowsInheritedAndExpired() {
        UUID[] a = schemeWithCodes(true);                 // code cha bao code con
        UUID au = roleId("AU");
        Map<String, Object> bulk = new HashMap<>();
        bulk.put("standardId", a[1]);
        bulk.put("assessmentRoleId", au);
        bulk.put("codeIds", List.of(codeId(a[2], "17"), codeId(a[2], "17.1"), codeId(a[2], "18")));
        bulk.put("includeGeneral", false);
        bulk.put("defaultValidityMonths", 36);
        bulk.put("effectiveFrom", "2024-01-01");
        postJson("/competency-definitions/bulk", adminToken(), bulk, HttpStatus.OK);

        String expertId = createExpert(manager.token(), "Ma trận " + uniq(""), "FULLTIME", null).path("id").asText();
        String path = "/experts/" + expertId + "/competencies";

        // không có định nghĩa (LA + 18) → không tự tạo nữa
        Map<String, Object> noDef = new HashMap<>();
        noDef.put("standardId", a[1]);
        noDef.put("codeId", codeId(a[2], "18"));
        noDef.put("assessmentRoleId", roleId("LA"));
        expectError(post(path, manager.token(), noDef), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
        // Văn phòng không đăng ký năng lực
        TestUser office = createUserWithRoles("DOCUMENT_CONTROLLER");
        Map<String, Object> au17 = new HashMap<>(noDef);
        au17.put("codeId", codeId(a[2], "17"));
        au17.put("assessmentRoleId", au);
        expectError(post(path, office.token(), au17), HttpStatus.FORBIDDEN, "FORBIDDEN");

        // AU code 17 (còn hiệu lực) + AU code 18 (đã hết hạn)
        String c17 = postJson(path, manager.token(), au17, HttpStatus.CREATED).path("id").asText();
        Map<String, Object> au18 = new HashMap<>(au17);
        au18.put("codeId", codeId(a[2], "18"));
        au18.put("effectiveFrom", LocalDate.now().minusYears(4).toString());
        au18.put("effectiveTo", LocalDate.now().minusDays(10).toString());
        String c18 = postJson(path, manager.token(), au18, HttpStatus.CREATED).path("id").asText();
        for (String c : List.of(c17, c18)) {
            postJson(path + "/" + c + "/actions", manager.token(), Map.of("action", "SUBMIT"), HttpStatus.OK);
            postJson(path + "/" + c + "/actions", reviewer.token(), Map.of("action", "START_REVIEW"), HttpStatus.OK);
            postJson(path + "/" + c + "/actions", director.token(), Map.of("action", "APPROVE"), HttpStatus.OK);
        }

        JsonNode m = getJson("/competency-matrix?standardId=" + a[1], manager.token());
        List<String> cols = new java.util.ArrayList<>();
        m.path("columns").forEach(c -> cols.add(c.path("codeValue").asText()));
        assertThat(cols).containsExactly("*", "17", "17.1", "18");
        JsonNode row = m.path("rows").get(0);
        assertThat(row.path("expertId").asText()).isEqualTo(expertId);
        assertThat(row.path("expertStatus").asText()).isEqualTo("DRAFT");
        Map<String, Boolean> inherited = new HashMap<>();
        row.path("cells").forEach(c -> inherited.put(c.path("codeValue").asText(), c.path("inherited").asBoolean()));
        assertThat(inherited).containsEntry("17", false).containsEntry("17.1", true).doesNotContainKey("18");  // 18 hết hạn: ẩn

        JsonNode withExpired = getJson("/competency-matrix?standardId=" + a[1] + "&includeExpired=true", manager.token());
        boolean expired18 = false;
        for (JsonNode c : withExpired.path("rows").get(0).path("cells")) {
            if (c.path("codeValue").asText().equals("18")) expired18 = c.path("expired").asBoolean();
        }
        assertThat(expired18).isTrue();
        assertThat(getJson("/competency-matrix?standardId=" + a[1] + "&roleId=" + roleId("LA"), manager.token())
                .path("rows")).isEmpty();
    }
}
