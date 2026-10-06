package com.npcore.ems.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MasterDataIntegrationTest extends AbstractIntegrationTest {

    /** CERTIFICATION_MANAGER có MASTER_DATA_MANAGE; admin cũng có. */
    private String manager;

    @BeforeEach
    void setUp() {
        manager = adminToken();
    }

    private JsonNode createScheme(String code) {
        return postJson("/schemes", manager, Map.of("schemeCode", code, "schemeName", "Scheme " + code,
                "parentCoversChild", true), HttpStatus.CREATED);
    }

    private JsonNode createCodeSet(String schemeId, String version, LocalDate from) {
        return postJson("/code-sets", manager, Map.of("schemeId", schemeId, "version", version,
                "effectiveFrom", from.toString()), HttpStatus.CREATED);
    }

    private JsonNode addCode(String setId, String value, String parent) {
        Map<String, Object> b = new HashMap<>();
        b.put("codeValue", value);
        b.put("codeName", "Code " + value);
        b.put("parentCode", parent);
        return postJson("/code-sets/" + setId + "/codes", manager, b, HttpStatus.CREATED);
    }

    private Map<String, JsonNode> codesByValue(String setId) {
        Map<String, JsonNode> out = new HashMap<>();
        getJson("/code-sets/" + setId + "/codes", manager).forEach(c -> out.put(c.path("codeValue").asText(), c));
        return out;
    }

    private JsonNode codeSet(String schemeId, String setId) {
        for (JsonNode cs : getJson("/code-sets?schemeId=" + schemeId, manager)) {
            if (cs.path("id").asText().equals(setId)) return cs;
        }
        throw new AssertionError("Không thấy code set " + setId);
    }

    @Test
    void schemeStandardAndVersionCrud() {
        String schemeCode = uniq("SC").toUpperCase();
        JsonNode scheme = createScheme(schemeCode.toLowerCase());
        String schemeId = scheme.path("id").asText();
        assertThat(scheme.path("schemeCode").asText()).isEqualTo(schemeCode);      // chuẩn hoá in hoa
        assertThat(scheme.path("status").asText()).isEqualTo("ACTIVE");
        expectError(post("/schemes", manager, Map.of("schemeCode", schemeCode, "schemeName", "dup")),
                HttpStatus.CONFLICT, "DUPLICATE");

        JsonNode updatedScheme = putJson("/schemes/" + schemeId, manager, Map.of("schemeCode", schemeCode,
                "schemeName", "Đổi tên", "parentCoversChild", false, "status", "INACTIVE"));
        assertThat(updatedScheme.path("schemeName").asText()).isEqualTo("Đổi tên");
        assertThat(updatedScheme.path("parentCoversChild").asBoolean()).isFalse();
        boolean listed = false;
        for (JsonNode s : getJson("/schemes", manager)) listed |= s.path("id").asText().equals(schemeId);
        assertThat(listed).isTrue();

        String stdCode = uniq("ISO-");
        JsonNode std = postJson("/standards", manager, Map.of("standardCode", stdCode, "standardName", "Std",
                "schemeId", schemeId), HttpStatus.CREATED);
        String stdId = std.path("id").asText();
        assertThat(std.path("schemeCode").asText()).isEqualTo(schemeCode);
        expectError(post("/standards", manager, Map.of("standardCode", stdCode.toLowerCase(), "standardName", "dup",
                "schemeId", schemeId)), HttpStatus.CONFLICT, "DUPLICATE");
        JsonNode stdUpdated = putJson("/standards/" + stdId, manager, Map.of("standardCode", stdCode,
                "standardName", "Std 2", "schemeId", schemeId, "status", "ACTIVE"));
        assertThat(stdUpdated.path("standardName").asText()).isEqualTo("Std 2");
        JsonNode bySheme = getJson("/standards?schemeId=" + schemeId, manager);
        assertThat(bySheme).hasSize(1);
        assertThat(bySheme.get(0).path("id").asText()).isEqualTo(stdId);
        assertThat(getJson("/standards", manager).size()).isGreaterThanOrEqualTo(1);

        JsonNode v1 = postJson("/standards/" + stdId + "/versions", manager, Map.of("version", "2015",
                "effectiveFrom", "2015-09-15"), HttpStatus.CREATED);
        assertThat(v1.path("status").asText()).isEqualTo("ACTIVE");               // mặc định của DB/entity
        expectError(post("/standards/" + stdId + "/versions", manager, Map.of("version", "2015",
                "effectiveFrom", "2016-01-01")), HttpStatus.CONFLICT, "DUPLICATE");
        expectError(post("/standards/" + stdId + "/versions", manager, Map.of("version", "bad",
                "effectiveFrom", "2016-01-01", "effectiveTo", "2015-01-01")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        JsonNode v1u = putJson("/standard-versions/" + v1.path("id").asText(), manager, Map.of("version", "2015",
                "effectiveFrom", "2015-09-15", "transitionEnd", "2018-09-15", "status", "TRANSITION"));
        assertThat(v1u.path("status").asText()).isEqualTo("TRANSITION");
        postJson("/standards/" + stdId + "/versions", manager, Map.of("version", "2026",
                "effectiveFrom", "2026-01-01", "status", "ACTIVE"), HttpStatus.CREATED);
        JsonNode versions = getJson("/standards/" + stdId + "/versions", manager);
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).path("version").asText()).isEqualTo("2026");   // mới nhất trước
    }

    @Test
    void codeSetTreeImportAndActivation() {
        String schemeId = createScheme(uniq("CS").toUpperCase()).path("id").asText();
        JsonNode v1 = createCodeSet(schemeId, "v1", LocalDate.of(2025, 1, 1));
        String v1Id = v1.path("id").asText();
        assertThat(v1.path("status").asText()).isEqualTo("DRAFT");
        expectError(post("/code-sets", manager, Map.of("schemeId", schemeId, "version", "V1", "effectiveFrom", "2025-02-01")),
                HttpStatus.CONFLICT, "DUPLICATE");

        JsonNode a = addCode(v1Id, "A", null);
        assertThat(a.path("level").asInt()).isEqualTo(1);
        assertThat(a.path("path").asText()).isEqualTo("/A/");
        JsonNode a1 = addCode(v1Id, "A1", "a");                         // mã cha không phân biệt hoa thường
        assertThat(a1.path("level").asInt()).isEqualTo(2);
        assertThat(a1.path("path").asText()).isEqualTo("/A/A1/");
        assertThat(a1.path("parentId").asText()).isEqualTo(a.path("id").asText());
        assertThat(a1.path("parentCode").asText()).isEqualToIgnoringCase("A");
        assertThat(codesByValue(v1Id).get("A1").path("parentCode").asText()).isEqualTo("A");
        expectError(post("/code-sets/" + v1Id + "/codes", manager, Map.of("codeValue", "a", "codeName", "dup")),
                HttpStatus.CONFLICT, "DUPLICATE");
        expectError(post("/code-sets/" + v1Id + "/codes", manager, Map.of("codeValue", "Z1", "codeName", "x", "parentCode", "NOPE")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(post("/code-sets/" + v1Id + "/codes", manager, Map.of("codeValue", "has space", "codeName", "x")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        // import: con (B1, B11) đứng TRƯỚC cha (B) trong file, có dòng trống ở giữa
        String csv = "code_value,code_name,parent_code,risk_category\n"
                + "B11,Con cấp 3,B1,HIGH\n"
                + "B1,Con cấp 2,B,\n"
                + "\n"
                + "B,Cha,,LOW\n"
                + "A2,Con của A có sẵn,A,\n";
        JsonNode imported = expect(uploadMultipart("/code-sets/" + v1Id + "/import", manager, "codes.csv", csv), HttpStatus.OK);
        assertThat(imported.path("errors")).isEmpty();
        assertThat(imported.path("total").asInt()).isEqualTo(4);
        assertThat(imported.path("imported").asInt()).isEqualTo(4);

        Map<String, JsonNode> codes = codesByValue(v1Id);
        assertThat(codes).containsOnlyKeys("A", "A1", "A2", "B", "B1", "B11");
        assertThat(codes.get("B11").path("path").asText()).isEqualTo("/B/B1/B11/");
        assertThat(codes.get("B11").path("level").asInt()).isEqualTo(3);
        assertThat(codes.get("B11").path("parentCode").asText()).isEqualTo("B1");
        assertThat(codes.get("B11").path("riskCategory").asText()).isEqualTo("HIGH");
        assertThat(codes.get("A2").path("path").asText()).isEqualTo("/A/A2/");
        assertThat(codeSet(schemeId, v1Id).path("codeCount").asLong()).isEqualTo(6);

        // import có mã cha không tồn tại → không nhập gì, báo đúng số dòng (dòng 3 trong file, tính cả header)
        String bad = "code_value,code_name,parent_code\n"
                + "C,Cha C,\n"
                + "C1,Con mồ côi,NOPE\n"
                + "C2,Con của C,C\n";
        JsonNode failed = expect(uploadMultipart("/code-sets/" + v1Id + "/import", manager, "bad.csv", bad), HttpStatus.OK);
        assertThat(failed.path("imported").asInt()).isZero();
        assertThat(failed.path("total").asInt()).isEqualTo(3);
        assertThat(failed.path("errors")).hasSize(1);
        assertThat(failed.path("errors").get(0).path("row").asInt()).isEqualTo(3);
        assertThat(failed.path("errors").get(0).path("message").asText()).contains("NOPE");
        assertThat(codesByValue(v1Id)).doesNotContainKeys("C", "C1", "C2");

        // dòng trùng mã đã có / trùng trong file
        String dup = "code_value,code_name,parent_code\nA,trùng,\nD,ok,\nD,trùng trong file,\n";
        JsonNode dupResult = expect(uploadMultipart("/code-sets/" + v1Id + "/import", manager, "dup.csv", dup), HttpStatus.OK);
        assertThat(dupResult.path("imported").asInt()).isZero();
        assertThat(dupResult.path("errors")).hasSize(2);
        assertThat(dupResult.path("errors").get(0).path("row").asInt()).isEqualTo(2);
        assertThat(dupResult.path("errors").get(1).path("row").asInt()).isEqualTo(4);

        // risk_category quá dài (cột VARCHAR(20)) → lỗi theo dòng, không phải lỗi DB
        String tooLong = "code_value,code_name,parent_code,risk_category\nF,ok,,\nG,risk dài,," + "X".repeat(25) + "\n";
        JsonNode longResult = expect(uploadMultipart("/code-sets/" + v1Id + "/import", manager, "long.csv", tooLong), HttpStatus.OK);
        assertThat(longResult.path("imported").asInt()).isZero();
        assertThat(longResult.path("errors")).hasSize(1);
        assertThat(longResult.path("errors").get(0).path("row").asInt()).isEqualTo(3);
        assertThat(codesByValue(v1Id)).doesNotContainKeys("F", "G");

        // file không hỗ trợ
        expectError(uploadMultipart("/code-sets/" + v1Id + "/import", manager, "codes.txt", "x"),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        // sửa code khi DRAFT
        JsonNode updatedCode = putJson("/codes/" + codes.get("B").path("id").asText(), manager,
                Map.of("codeName", "Cha đổi tên", "status", "ACTIVE"));
        assertThat(updatedCode.path("codeName").asText()).isEqualTo("Cha đổi tên");

        // activate v1
        JsonNode activated = postJson("/code-sets/" + v1Id + "/activate", manager, null, HttpStatus.OK);
        assertThat(activated.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(activated.path("codeCount").asLong()).isEqualTo(6);
        expectError(post("/code-sets/" + v1Id + "/activate", manager, null), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");

        // bộ ACTIVE: không thêm / sửa / import
        expectError(post("/code-sets/" + v1Id + "/codes", manager, Map.of("codeValue", "E", "codeName", "x")),
                HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
        expectError(put("/codes/" + codes.get("B").path("id").asText(), manager, Map.of("codeName", "x")),
                HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
        expectError(uploadMultipart("/code-sets/" + v1Id + "/import", manager, "c.csv", "code_value,code_name\nE,x\n"),
                HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");

        // v2 rỗng không activate được
        JsonNode v2 = createCodeSet(schemeId, "v2", LocalDate.of(2026, 1, 1));
        String v2Id = v2.path("id").asText();
        expectError(post("/code-sets/" + v2Id + "/activate", manager, null), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
        addCode(v2Id, "A", null);
        JsonNode v2Active = postJson("/code-sets/" + v2Id + "/activate", manager, null, HttpStatus.OK);
        assertThat(v2Active.path("status").asText()).isEqualTo("ACTIVE");

        JsonNode v1After = codeSet(schemeId, v1Id);
        assertThat(v1After.path("status").asText()).isEqualTo("RETIRED");
        assertThat(v1After.path("effectiveTo").asText()).isEqualTo("2025-12-31");
        assertThat(codeSet(schemeId, v2Id).path("status").asText()).isEqualTo("ACTIVE");
        // code của bộ cũ vẫn giữ nguyên
        assertThat(codesByValue(v1Id)).hasSize(6);
    }

    @Test
    void writeNeedsMasterDataManageButReadIsOpenToAuthenticatedUsers() {
        TestUser expert = createUserWithRoles("EXPERT");
        TestUser director = createUserWithRoles("CERTIFICATION_DIRECTOR");
        for (TestUser u : new TestUser[] {expert, director}) {
            expectError(post("/schemes", u.token(), Map.of("schemeCode", uniq("X"), "schemeName", "x")),
                    HttpStatus.FORBIDDEN, "FORBIDDEN");
            expectError(post("/industries", u.token(), Map.of("industryCode", uniq("X"), "industryName", "x")),
                    HttpStatus.FORBIDDEN, "FORBIDDEN");
            expect(get("/schemes", u.token()), HttpStatus.OK);
            expect(get("/standards", u.token()), HttpStatus.OK);
            expect(get("/code-sets", u.token()), HttpStatus.OK);
            expect(get("/degree-levels", u.token()), HttpStatus.OK);
            expect(get("/document-types", u.token()), HttpStatus.OK);
        }
        expectError(get("/schemes", null), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");

        // CERTIFICATION_MANAGER có MASTER_DATA_MANAGE
        TestUser cm = createUserWithRoles("CERTIFICATION_MANAGER");
        expect(post("/schemes", cm.token(), Map.of("schemeCode", uniq("CM").toUpperCase(), "schemeName", "x")), HttpStatus.CREATED);
    }

    @Test
    void simpleCatalogs() {
        String ind = uniq("IND");
        JsonNode industry = postJson("/industries", manager, Map.of("industryCode", ind, "industryName", "Ngành"), HttpStatus.CREATED);
        expectError(post("/industries", manager, Map.of("industryCode", ind.toLowerCase(), "industryName", "dup")),
                HttpStatus.CONFLICT, "DUPLICATE");
        JsonNode upd = putJson("/industries/" + industry.path("id").asText(), manager,
                Map.of("industryCode", ind, "industryName", "Ngành 2"));
        assertThat(upd.path("industryName").asText()).isEqualTo("Ngành 2");

        JsonNode degrees = getJson("/degree-levels", manager);
        assertThat(degrees.get(0).path("code").asText()).isEqualTo("COLLEGE");
        boolean certNeedsExpiry = false;
        for (JsonNode t : getJson("/document-types", manager)) {
            if (t.path("code").asText().equals("CERTIFICATE")) certNeedsExpiry = t.path("requiresExpiry").asBoolean();
        }
        assertThat(certNeedsExpiry).isTrue();
    }
}
