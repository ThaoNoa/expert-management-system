package com.npcore.ems.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DocumentIntegrationTest extends AbstractIntegrationTest {

    /** CERTIFICATION_MANAGER: DOCUMENT_MANAGE:ALL, không có DOCUMENT_VERIFY. */
    private TestUser manager;
    /** DOCUMENT_CONTROLLER: DOCUMENT_MANAGE + DOCUMENT_VERIFY. */
    private TestUser controllerA;
    private TestUser controllerB;

    @BeforeEach
    void setUp() {
        manager = createUserWithRoles("CERTIFICATION_MANAGER");
        controllerA = createUserWithRoles("DOCUMENT_CONTROLLER");
        controllerB = createUserWithRoles("DOCUMENT_CONTROLLER");
    }

    /** Nội dung file ngẫu nhiên → SHA-256 không trùng giữa các lần chạy. */
    private static byte[] randomBytes() {
        byte[] b = new byte[2048];
        ThreadLocalRandom.current().nextBytes(b);
        return b;
    }

    private ResponseEntity<String> upload(String token, byte[] content, String type, Map<String, Object> extra) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("documentTypeCode", type);
        fields.put("title", "Tài liệu " + uniq(""));
        if (extra != null) fields.putAll(extra);
        return uploadMultipart("/documents", token, "ho-so.pdf", content, fields);
    }

    @Test
    void uploadDeduplicatesBySha256() {
        byte[] content = randomBytes();
        JsonNode first = expect(upload(controllerA.token(), content, "CV", null), HttpStatus.CREATED);
        assertThat(first.path("duplicate").asBoolean()).isFalse();
        JsonNode doc = first.path("document");
        String docId = doc.path("id").asText();
        assertThat(doc.path("documentTypeCode").asText()).isEqualTo("CV");
        JsonNode v1 = doc.path("currentVersion");
        assertThat(v1.path("versionNo").asInt()).isEqualTo(1);
        assertThat(v1.path("fileName").asText()).isEqualTo("ho-so.pdf");
        assertThat(v1.path("fileSize").asLong()).isEqualTo(content.length);
        assertThat(v1.path("sha256").asText()).hasSize(64);
        assertThat(v1.path("status").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(v1.path("uploadedBy").asText()).isEqualTo(controllerA.id.toString());

        // cùng nội dung, khác tên file, người khác upload → trả document cũ
        JsonNode second = expect(uploadMultipart("/documents", manager.token(), "ten-khac.pdf", content,
                Map.of("documentTypeCode", "CV")), HttpStatus.OK);
        assertThat(second.path("duplicate").asBoolean()).isTrue();
        assertThat(second.path("document").path("id").asText()).isEqualTo(docId);
        assertThat(second.path("document").path("versions")).hasSize(1);

        JsonNode fetched = getJson("/documents/" + docId, manager.token());
        assertThat(fetched.path("versions")).hasSize(1);
    }

    @Test
    void certificateRequiresExpiryAndDatesAreValidated() {
        expectError(upload(controllerA.token(), randomBytes(), "CERTIFICATE", null), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(upload(controllerA.token(), randomBytes(), "CERTIFICATE",
                Map.of("issuedDate", "2025-01-01", "expiryDate", "2024-01-01")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(upload(controllerA.token(), randomBytes(), "NO_SUCH_TYPE", null), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(upload(controllerA.token(), new byte[0], "CV", null), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(upload(controllerA.token(), randomBytes(), "CV", Map.of("linkObjectType", "PLANET",
                "linkObjectId", UUID.randomUUID().toString())), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");

        String expiry = LocalDate.now().plusYears(2).toString();
        JsonNode ok = expect(upload(controllerA.token(), randomBytes(), "CERTIFICATE",
                Map.of("issuedDate", "2025-01-01", "expiryDate", expiry)), HttpStatus.CREATED);
        assertThat(ok.path("document").path("currentVersion").path("expiryDate").asText()).isEqualTo(expiry);
    }

    @Test
    void verificationNeedsADifferentUser() {
        JsonNode doc = expect(upload(controllerA.token(), randomBytes(), "CV", null), HttpStatus.CREATED).path("document");
        String versionId = doc.path("currentVersion").path("id").asText();
        String verify = "/document-versions/" + versionId + "/verify";

        expectError(post(verify, controllerA.token(), null), HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE");
        expectError(post(verify, manager.token(), null), HttpStatus.FORBIDDEN, "FORBIDDEN");   // không có DOCUMENT_VERIFY

        expect(post(verify, controllerB.token(), null), HttpStatus.NO_CONTENT);
        JsonNode v = getJson("/documents/" + doc.path("id").asText(), manager.token()).path("currentVersion");
        assertThat(v.path("status").asText()).isEqualTo("VERIFIED");
        assertThat(v.path("verifiedBy").asText()).isEqualTo(controllerB.id.toString());
        assertThat(v.path("verifiedAt").isNull()).isFalse();

        // đã VERIFIED thì không verify lại
        expectError(post(verify, controllerB.token(), null), HttpStatus.CONFLICT, "ILLEGAL_TRANSITION");

        JsonNode filtered = getJson("/documents?status=VERIFIED&q=" + doc.path("title").asText(), manager.token());
        assertThat(filtered.path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void rejectNeedsReason() {
        JsonNode doc = expect(upload(controllerA.token(), randomBytes(), "CV", null), HttpStatus.CREATED).path("document");
        String reject = "/document-versions/" + doc.path("currentVersion").path("id").asText() + "/reject";
        expectError(post(reject, controllerB.token(), Map.of("reason", " ")), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expect(post(reject, controllerB.token(), Map.of("reason", "Ảnh mờ")), HttpStatus.NO_CONTENT);
        JsonNode v = getJson("/documents/" + doc.path("id").asText(), manager.token()).path("currentVersion");
        assertThat(v.path("status").asText()).isEqualTo("REJECTED");
        assertThat(v.path("rejectReason").asText()).isEqualTo("Ảnh mờ");
    }

    @Test
    void newVersionSupersedesPreviousAndDownloadReturnsSameBytes() {
        byte[] v1Bytes = randomBytes();
        JsonNode doc = expect(upload(controllerA.token(), v1Bytes, "CV", null), HttpStatus.CREATED).path("document");
        String docId = doc.path("id").asText();
        String v1Id = doc.path("currentVersion").path("id").asText();
        expect(post("/document-versions/" + v1Id + "/verify", controllerB.token(), null), HttpStatus.NO_CONTENT);

        byte[] v2Bytes = randomBytes();
        ResponseEntity<String> r = uploadMultipart("/documents/" + docId + "/versions", controllerA.token(), "ho-so-v2.pdf",
                v2Bytes, Map.of());
        JsonNode detail = expect(r, HttpStatus.OK);
        assertThat(detail.path("currentVersion").path("versionNo").asInt()).isEqualTo(2);
        assertThat(detail.path("currentVersion").path("status").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(detail.path("versions")).hasSize(2);
        Map<Integer, String> statusByNo = new HashMap<>();
        detail.path("versions").forEach(v -> statusByNo.put(v.path("versionNo").asInt(), v.path("status").asText()));
        assertThat(statusByNo).containsEntry(1, "SUPERSEDED").containsEntry(2, "PENDING_VERIFICATION");
        String v2Id = detail.path("currentVersion").path("id").asText();

        // version mới trùng file đã có → 409
        expectError(uploadMultipart("/documents/" + docId + "/versions", controllerA.token(), "x.pdf", v1Bytes, Map.of()),
                HttpStatus.CONFLICT, "DUPLICATE");

        ResponseEntity<byte[]> d1 = getBytes("/document-versions/" + v1Id + "/download", manager.token());
        assertThat(d1.getStatusCode().value()).as("download v1: %s", d1.getBody() == null ? "" : new String(d1.getBody(), StandardCharsets.UTF_8))
                .isEqualTo(200);
        assertThat(d1.getBody()).isEqualTo(v1Bytes);
        assertThat(d1.getHeaders().getContentDisposition().getFilename()).isEqualTo("ho-so.pdf");
        ResponseEntity<byte[]> d2 = getBytes("/document-versions/" + v2Id + "/download", manager.token());
        assertThat(d2.getStatusCode().value()).isEqualTo(200);
        assertThat(d2.getBody()).isEqualTo(v2Bytes);

        JsonNode audit = getJson("/audit-logs?objectType=DOCUMENT&action=DOWNLOAD&objectId=" + docId, adminToken());
        assertThat(audit.path("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void linksAreCreatedOnceAndCanBeRemoved() {
        String expertId = createExpert(manager.token(), "Link " + uniq(""), "FULLTIME", null).path("id").asText();
        JsonNode doc = expect(upload(manager.token(), randomBytes(), "CV",
                Map.of("ownerExpertId", expertId, "linkObjectType", "EXPERT", "linkObjectId", expertId)), HttpStatus.CREATED)
                .path("document");
        String docId = doc.path("id").asText();
        assertThat(doc.path("ownerExpertId").asText()).isEqualTo(expertId);
        assertThat(doc.path("ownerExpertName").asText()).startsWith("Link ");
        assertThat(doc.path("links")).hasSize(1);
        assertThat(doc.path("links").get(0).path("objectType").asText()).isEqualTo("EXPERT");

        expectError(post("/documents/" + docId + "/links", manager.token(), Map.of("objectType", "EXPERT", "objectId", expertId)),
                HttpStatus.CONFLICT, "DUPLICATE");
        JsonNode link = postJson("/documents/" + docId + "/links", manager.token(),
                Map.of("objectType", "CERTIFICATE", "objectId", UUID.randomUUID().toString(), "purpose", "bằng chứng"),
                HttpStatus.CREATED);
        assertThat(getJson("/documents/" + docId, manager.token()).path("links")).hasSize(2);
        expect(delete("/document-links/" + link.path("id").asText(), manager.token()), HttpStatus.NO_CONTENT);
        assertThat(getJson("/documents/" + docId, manager.token()).path("links")).hasSize(1);

        JsonNode byOwner = getJson("/documents?ownerExpertId=" + expertId, manager.token());
        assertThat(byOwner.path("totalElements").asLong()).isEqualTo(1);
        assertThat(getJson("/experts/" + expertId, manager.token()).path("counts").path("documents").asLong()).isEqualTo(1);
    }

    @Test
    void expertSeesOnlyOwnDocuments() {
        TestUser expertUser = createUserWithRoles("EXPERT");
        String ownExpert = createExpert(manager.token(), "Own " + uniq(""), "FULLTIME", expertUser.id).path("id").asText();
        String otherExpert = createExpert(manager.token(), "Other " + uniq(""), "FULLTIME", null).path("id").asText();

        JsonNode ownDoc = expect(upload(manager.token(), randomBytes(), "CV", Map.of("ownerExpertId", ownExpert)),
                HttpStatus.CREATED).path("document");
        byte[] otherBytes = randomBytes();
        JsonNode otherDoc = expect(upload(manager.token(), otherBytes, "CV", Map.of("ownerExpertId", otherExpert)),
                HttpStatus.CREATED).path("document");
        JsonNode unowned = expect(upload(manager.token(), randomBytes(), "SOP", null), HttpStatus.CREATED).path("document");

        String token = expertUser.token();
        // chuyên gia tự upload: owner tự gán là hồ sơ của mình
        JsonNode selfUpload = expect(upload(token, randomBytes(), "TRAINING", null), HttpStatus.CREATED).path("document");
        assertThat(selfUpload.path("ownerExpertId").asText()).isEqualTo(ownExpert);
        expectError(upload(token, randomBytes(), "TRAINING", Map.of("ownerExpertId", otherExpert)), HttpStatus.FORBIDDEN, "FORBIDDEN");

        JsonNode list = getJson("/documents?size=100", token);
        Set<String> ids = new HashSet<>();
        list.path("content").forEach(d -> {
            ids.add(d.path("id").asText());
            assertThat(d.path("ownerExpertId").asText()).isEqualTo(ownExpert);
        });
        assertThat(ids).containsExactlyInAnyOrder(ownDoc.path("id").asText(), selfUpload.path("id").asText());

        expect(get("/documents/" + ownDoc.path("id").asText(), token), HttpStatus.OK);
        expectError(get("/documents/" + otherDoc.path("id").asText(), token), HttpStatus.NOT_FOUND, "NOT_FOUND");
        expectError(get("/documents/" + unowned.path("id").asText(), token), HttpStatus.NOT_FOUND, "NOT_FOUND");
        ResponseEntity<byte[]> dl = getBytes("/document-versions/" + otherDoc.path("currentVersion").path("id").asText()
                + "/download", token);
        assertThat(dl.getStatusCode().value()).isEqualTo(404);
        expectError(get("/documents?ownerExpertId=" + otherExpert, token), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expectError(uploadMultipart("/documents/" + otherDoc.path("id").asText() + "/versions", token, "x.pdf", randomBytes(), Map.of()),
                HttpStatus.NOT_FOUND, "NOT_FOUND");

        // upload trùng file của người khác → không lộ tài liệu đó
        JsonNode dup = expectError(upload(token, otherBytes, "CV", null), HttpStatus.CONFLICT, "DUPLICATE");
        assertThat(dup.has("document")).isFalse();

        // chuyên gia không có DOCUMENT_VERIFY
        expectError(post("/document-versions/" + ownDoc.path("currentVersion").path("id").asText() + "/verify", token, null),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
}
