package com.npcore.ems.competency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
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
        assertThat(matrixBody.isArray()).isTrue();
        assertThat(matrixBody.size()).isGreaterThanOrEqualTo(1);
    }
}
