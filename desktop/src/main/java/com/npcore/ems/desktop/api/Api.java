package com.npcore.ems.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.desktop.api.Dtos.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Toàn bộ endpoint Phase 1 dưới dạng phương thức có kiểu (xem docs/api-phase1.md). Gọi ngoài JavaFX thread. */
public final class Api {

    private final ApiClient c;

    public Api(ApiClient client) {
        this.c = client;
    }

    public ApiClient client() { return c; }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ---------------------------------------------------------------- Auth
    public TokenResponse login(String username, String password) {
        TokenResponse t = c.post("/auth/login", Map.of("username", username, "password", password), TokenResponse.class);
        c.setTokens(t.accessToken(), t.refreshToken());
        return t;
    }

    public void logout() {
        String rt = c.refreshToken();
        try {
            if (rt != null) c.post("/auth/logout", Map.of("refreshToken", rt));
        } finally {
            c.setTokens(null, null);
        }
    }

    public Me me() { return c.get("/auth/me", Me.class); }

    public void changePassword(String current, String next) {
        c.post("/auth/change-password", Map.of("currentPassword", current, "newPassword", next));
    }

    // ---------------------------------------------------------------- Users & roles
    public Page<User> users(String q, String status, int page, int size) {
        return c.get("/users" + ApiClient.query(params("q", q, "status", status, "page", page, "size", size)),
                new TypeReference<Page<User>>() {});
    }

    public User createUser(CreateUserRequest r) { return c.post("/users", r, User.class); }

    public User updateUser(UUID id, UpdateUserRequest r) { return c.put("/users/" + id, r, User.class); }

    public void setUserEnabled(UUID id, boolean enabled) { c.post("/users/" + id + (enabled ? "/enable" : "/disable"), null); }

    public void resetPassword(UUID id, String password) { c.post("/users/" + id + "/reset-password", Map.of("newPassword", password)); }

    public void deleteUser(UUID id) { c.delete("/users/" + id); }

    public List<Role> roles() { return c.get("/roles", new TypeReference<List<Role>>() {}); }

    public Role createRole(RoleRequest r) { return c.post("/roles", r, Role.class); }

    public Role updateRole(UUID id, RoleRequest r) { return c.put("/roles/" + id, r, Role.class); }

    public void deleteRole(UUID id) { c.delete("/roles/" + id); }

    public List<Permission> permissions() { return c.get("/permissions", new TypeReference<List<Permission>>() {}); }

    // ---------------------------------------------------------------- Master data
    /** Danh mục đơn giản: GET list / POST / PUT theo đường dẫn tài nguyên (departments, industries...). */
    public <T> List<T> list(String resource, TypeReference<List<T>> type) { return c.get("/" + resource, type); }

    public JsonNode create(String resource, Object body) { return c.post("/" + resource, body, JsonNode.class); }

    public JsonNode update(String resource, UUID id, Object body) { return c.put("/" + resource + "/" + id, body, JsonNode.class); }

    public List<Department> departments() { return list("departments", new TypeReference<>() {}); }

    public List<AssessmentRole> assessmentRoles() { return list("assessment-roles", new TypeReference<>() {}); }

    public List<Scheme> schemes() { return list("schemes", new TypeReference<>() {}); }

    public List<Industry> industries() { return list("industries", new TypeReference<>() {}); }

    public List<Location> locations() { return list("locations", new TypeReference<>() {}); }

    public List<EducationField> educationFields() { return list("education-fields", new TypeReference<>() {}); }

    public List<DegreeLevel> degreeLevels() { return list("degree-levels", new TypeReference<>() {}); }

    public List<DocumentType> documentTypes() { return list("document-types", new TypeReference<>() {}); }

    public List<Standard> standards(UUID schemeId) {
        return c.get("/standards" + ApiClient.query(params("schemeId", schemeId)), new TypeReference<List<Standard>>() {});
    }

    public List<StandardVersion> standardVersions(UUID standardId) {
        return c.get("/standards/" + standardId + "/versions", new TypeReference<List<StandardVersion>>() {});
    }

    public StandardVersion addStandardVersion(UUID standardId, Object body) {
        return c.post("/standards/" + standardId + "/versions", body, StandardVersion.class);
    }

    public StandardVersion updateStandardVersion(UUID versionId, Object body) {
        return c.put("/standard-versions/" + versionId, body, StandardVersion.class);
    }

    public List<CodeSet> codeSets(UUID schemeId) {
        return c.get("/code-sets" + ApiClient.query(params("schemeId", schemeId)), new TypeReference<List<CodeSet>>() {});
    }

    public CodeSet createCodeSet(Object body) { return c.post("/code-sets", body, CodeSet.class); }

    public CodeSet activateCodeSet(UUID id) { return c.post("/code-sets/" + id + "/activate", null, CodeSet.class); }

    public List<Code> codes(UUID codeSetId) { return c.get("/code-sets/" + codeSetId + "/codes", new TypeReference<List<Code>>() {}); }

    public Code addCode(UUID codeSetId, Object body) { return c.post("/code-sets/" + codeSetId + "/codes", body, Code.class); }

    public Code updateCode(UUID codeId, Object body) { return c.put("/codes/" + codeId, body, Code.class); }

    public ImportResult importCodes(UUID codeSetId, Path file) {
        return c.upload("/code-sets/" + codeSetId + "/import", Map.of(), file, ImportResult.class);
    }

    // ---------------------------------------------------------------- Documents
    public UploadResult uploadDocument(Path file, String typeCode, String title, UUID ownerExpertId, LocalDate issued,
                                       LocalDate expiry, String linkType, UUID linkId) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("documentTypeCode", typeCode);
        f.put("title", title);
        f.put("ownerExpertId", str(ownerExpertId));
        f.put("issuedDate", str(issued));
        f.put("expiryDate", str(expiry));
        f.put("linkObjectType", linkType);
        f.put("linkObjectId", str(linkId));
        return c.upload("/documents", f, file, UploadResult.class);
    }

    public Page<DocumentSummary> documents(UUID ownerExpertId, String typeCode, String status, String q, int page, int size) {
        return c.get("/documents" + ApiClient.query(params("ownerExpertId", ownerExpertId, "documentTypeCode", typeCode,
                "status", status, "q", q, "page", page, "size", size)), new TypeReference<Page<DocumentSummary>>() {});
    }

    public DocumentDetail document(UUID id) { return c.get("/documents/" + id, DocumentDetail.class); }

    public DocumentDetail newDocumentVersion(UUID id, Path file, LocalDate issued, LocalDate expiry) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("issuedDate", str(issued));
        f.put("expiryDate", str(expiry));
        return c.upload("/documents/" + id + "/versions", f, file, DocumentDetail.class);
    }

    public Path downloadVersion(UUID versionId, Path dir, String fallbackName) {
        return c.download("/document-versions/" + versionId + "/download", dir, fallbackName);
    }

    public void verifyVersion(UUID versionId) { c.post("/document-versions/" + versionId + "/verify", null); }

    public void rejectVersion(UUID versionId, String reason) { c.post("/document-versions/" + versionId + "/reject", Map.of("reason", reason)); }

    public Link addLink(UUID documentId, String objectType, UUID objectId, String purpose) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("objectType", objectType);
        b.put("objectId", objectId);
        b.put("purpose", purpose);
        return c.post("/documents/" + documentId + "/links", b, Link.class);
    }

    public void removeLink(UUID linkId) { c.delete("/document-links/" + linkId); }

    // ---------------------------------------------------------------- Experts
    public Page<ExpertSummary> experts(String q, String expertType, String employmentType, String status, int page,
                                       int size, String sort) {
        return c.get("/experts" + ApiClient.query(params("q", q, "expertType", expertType, "employmentType", employmentType,
                "status", status, "page", page, "size", size, "sort", sort)), new TypeReference<Page<ExpertSummary>>() {});
    }

    public ExpertDetail createExpert(ExpertRequest r) { return c.post("/experts", r, ExpertDetail.class); }

    public ExpertDetail expert(UUID id) { return c.get("/experts/" + id, ExpertDetail.class); }

    public ExpertDetail myExpert() { return c.get("/experts/me", ExpertDetail.class); }

    public ExpertDetail updateExpert(UUID id, ExpertRequest r) { return c.put("/experts/" + id, r, ExpertDetail.class); }

    public ExpertDetail changeExpertStatus(UUID id, String action, String comment, LocalDate suspendedUntil) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("action", action);
        b.put("comment", comment);
        b.put("suspendedUntil", suspendedUntil);
        return c.post("/experts/" + id + "/status", b, ExpertDetail.class);
    }

    public List<HistoryEntry> expertHistory(UUID id) {
        return c.get("/experts/" + id + "/history", new TypeReference<List<HistoryEntry>>() {});
    }

    public <T> List<T> expertItems(UUID expertId, String kind, TypeReference<List<T>> type) {
        return c.get("/experts/" + expertId + "/" + kind, type);
    }

    public void createExpertItem(UUID expertId, String kind, Object body) { c.post("/experts/" + expertId + "/" + kind, body, JsonNode.class); }

    public void updateExpertItem(UUID expertId, String kind, Object itemId, Object body) {
        c.put("/experts/" + expertId + "/" + kind + "/" + itemId, body, JsonNode.class);
    }

    public void deleteExpertItem(UUID expertId, String kind, Object itemId) { c.delete("/experts/" + expertId + "/" + kind + "/" + itemId); }

    public ImportResult importExperts(Path file) { return c.upload("/experts/import", Map.of(), file, ImportResult.class); }

    public Path downloadExpertTemplate(Path dir) { return c.download("/experts/import/template", dir, "expert-import-template.csv"); }

    // ---------------------------------------------------------------- Audit & settings
    public Page<AuditLog> auditLogs(String objectType, String objectId, String action, LocalDate from, LocalDate to,
                                    int page, int size) {
        return c.get("/audit-logs" + ApiClient.query(params("objectType", objectType, "objectId", objectId,
                "action", action, "from", from, "to", to, "page", page, "size", size)), new TypeReference<Page<AuditLog>>() {});
    }

    public List<Setting> settings() { return c.get("/settings", new TypeReference<List<Setting>>() {}); }

    public Setting updateSetting(String key, JsonNode value) { return c.put("/settings/" + key, Map.of("value", value), Setting.class); }

    // ---------------------------------------------------------------- Competency
    public Page<CompetencyDefinition> competencyDefinitions(UUID schemeId, UUID standardId, String status, int page, int size) {
        return c.get("/competency-definitions" + ApiClient.query(params("schemeId", schemeId, "standardId", standardId,
                "status", status, "page", page, "size", size)), new TypeReference<Page<CompetencyDefinition>>() {});
    }

    public List<CompetencyDefinition> activeCompetencyDefinitions(UUID standardId) {
        return c.get("/competency-definitions/active" + ApiClient.query(params("standardId", standardId)),
                new TypeReference<List<CompetencyDefinition>>() {});
    }

    public CompetencyDefinition createCompetencyDefinition(CompetencyDefinitionRequest r) {
        return c.post("/competency-definitions", r, CompetencyDefinition.class);
    }

    public CompetencyDefinition updateCompetencyDefinition(UUID id, CompetencyDefinitionRequest r) {
        return c.put("/competency-definitions/" + id, r, CompetencyDefinition.class);
    }

    public void activateCompetencyDefinition(UUID id) { c.post("/competency-definitions/" + id + "/activate", null); }

    public void retireCompetencyDefinition(UUID id) { c.post("/competency-definitions/" + id + "/retire", null); }

    public Page<ExpertCompetency> expertCompetencies(UUID expertId, String status, int page, int size) {
        return c.get("/experts/" + expertId + "/competencies" + ApiClient.query(params("status", status, "page", page, "size", size)),
                new TypeReference<Page<ExpertCompetency>>() {});
    }

    public ExpertCompetency expertCompetency(UUID expertId, UUID id) {
        return c.get("/experts/" + expertId + "/competencies/" + id, ExpertCompetency.class);
    }

    public ExpertCompetency addExpertCompetency(UUID expertId, ExpertCompetencyRequest r) {
        return c.post("/experts/" + expertId + "/competencies", r, ExpertCompetency.class);
    }

    public ExpertCompetency transitionExpertCompetency(UUID expertId, UUID id, String action, String comment) {
        return c.post("/experts/" + expertId + "/competencies/" + id + "/actions",
                new CompetencyActionRequest(action, comment), ExpertCompetency.class);
    }

    public Evidence addCompetencyEvidence(UUID expertId, UUID id, EvidenceRequest r) {
        return c.post("/experts/" + expertId + "/competencies/" + id + "/evidences", r, Evidence.class);
    }

    public void removeCompetencyEvidence(UUID expertId, UUID id, UUID evidenceId) {
        c.delete("/experts/" + expertId + "/competencies/" + id + "/evidences/" + evidenceId);
    }

    public List<MatrixRow> competencyMatrix(UUID standardId) {
        return c.get("/competency-matrix" + ApiClient.query(params("standardId", standardId)),
                new TypeReference<List<MatrixRow>>() {});
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
