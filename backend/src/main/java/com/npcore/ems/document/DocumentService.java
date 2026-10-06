package com.npcore.ems.document;

import com.npcore.ems.document.DocumentDtos.DocumentDetail;
import com.npcore.ems.document.DocumentDtos.DocumentSummary;
import com.npcore.ems.document.DocumentDtos.LinkDto;
import com.npcore.ems.document.DocumentDtos.UploadResult;
import com.npcore.ems.document.DocumentDtos.VersionDto;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.security.CurrentUser;
import com.npcore.ems.shared.security.ExpertOwnership;
import com.npcore.ems.shared.security.SecurityUtils;
import com.npcore.ems.shared.storage.StorageService;
import com.npcore.ems.shared.web.ApiException;
import com.npcore.ems.shared.web.PageResponse;
import com.npcore.ems.shared.workflow.WorkflowService;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Kho tài liệu (Module 12): chống upload trùng bằng SHA-256, version, liên kết đa hình, xác minh 2 người.
 * Phạm vi: user có quyền :ALL xem mọi tài liệu; chuyên gia (OWN) chỉ tài liệu của mình.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    static final Set<String> LINK_TYPES = Set.of("EXPERT", "EDUCATION", "EXPERIENCE", "TRAINING", "CERTIFICATE",
            "COMPETENCY", "APPROVAL", "WITNESS", "ANNUAL_REVIEW", "RESTRICTION", "IMPARTIALITY", "ASSESSMENT_EVENT");

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final DocumentLinkRepository links;
    private final DocumentTypeRepository types;
    private final StorageService storage;
    private final WorkflowService workflow;
    private final ExpertOwnership ownership;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public record UploadCommand(MultipartFile file, String documentTypeCode, String title, UUID ownerExpertId,
                                LocalDate issuedDate, LocalDate expiryDate, String linkObjectType, UUID linkObjectId) {}

    public record Download(String fileName, String contentType, long size, InputStream content) {}

    @Transactional(readOnly = true)
    public List<DocumentDtos.DocumentTypeDto> types() {
        return types.findAll().stream()
                .map(t -> new DocumentDtos.DocumentTypeDto(t.getCode(), t.getName(), t.isRequiresExpiry(), t.isRequiresVerification()))
                .toList();
    }

    @Transactional
    public UploadResult upload(UploadCommand cmd) {
        CurrentUser user = SecurityUtils.currentUser();
        if (cmd.file() == null || cmd.file().isEmpty()) throw ApiException.badRequest("Chưa chọn file");
        DocumentType type = types.findById(cmd.documentTypeCode())
                .orElseThrow(() -> ApiException.badRequest("Loại tài liệu không hợp lệ: " + cmd.documentTypeCode()));
        UUID owner = resolveOwnerForWrite(user, cmd.ownerExpertId());
        validateLinkType(cmd.linkObjectType(), cmd.linkObjectId());
        validateDates(type, cmd.issuedDate(), cmd.expiryDate());

        Path temp = toTempFile(cmd.file());
        try {
            String sha = sha256(temp);
            Optional<DocumentVersion> existing = versions.findBySha256(sha);
            if (existing.isPresent()) {                                   // BR-12.1.1: không lưu file trùng
                Document doc = documents.findById(existing.get().getDocumentId()).orElseThrow();
                if (!canRead(user, doc)) throw ApiException.duplicate("File này đã tồn tại trong hệ thống");
                if (cmd.linkObjectType() != null) link(doc.getId(), cmd.linkObjectType(), cmd.linkObjectId(), null);
                audit.record("REUSE", "DOCUMENT", doc.getId(), null, Map.of("sha256", sha), "Upload trùng file");
                return new UploadResult(true, detail(doc));
            }
            Document doc = new Document();
            doc.setType(type);
            doc.setTitle(cmd.title() == null || cmd.title().isBlank() ? cmd.file().getOriginalFilename() : cmd.title().trim());
            doc.setOwnerExpertId(owner);
            doc.setCreatedBy(user.id());
            documents.saveAndFlush(doc);
            DocumentVersion v = storeVersion(doc, 1, cmd.file(), temp, sha, cmd.issuedDate(), cmd.expiryDate(), user);
            doc.setCurrentVersionId(v.getId());
            if (cmd.linkObjectType() != null) link(doc.getId(), cmd.linkObjectType(), cmd.linkObjectId(), null);
            documents.flush();
            DocumentDetail d = detail(doc);
            audit.record("UPLOAD", "DOCUMENT", doc.getId(), null, d, null);
            return new UploadResult(false, d);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Transactional
    public DocumentDetail newVersion(UUID documentId, MultipartFile file, LocalDate issued, LocalDate expiry) {
        CurrentUser user = SecurityUtils.currentUser();
        Document doc = loadReadable(user, documentId);
        resolveOwnerForWrite(user, doc.getOwnerExpertId());
        validateDates(doc.getType(), issued, expiry);
        Path temp = toTempFile(file);
        try {
            String sha = sha256(temp);
            if (versions.findBySha256(sha).isPresent()) throw ApiException.duplicate("File này đã tồn tại trong hệ thống");
            List<DocumentVersion> all = versions.findByDocumentIdOrderByVersionNoDesc(documentId);
            int next = all.isEmpty() ? 1 : all.get(0).getVersionNo() + 1;
            all.stream().filter(v -> v.getStatus().equals("VERIFIED") || v.getStatus().equals("PENDING_VERIFICATION"))
                    .forEach(v -> v.setStatus("SUPERSEDED"));
            DocumentVersion v = storeVersion(doc, next, file, temp, sha, issued, expiry, user);
            doc.setCurrentVersionId(v.getId());
            documents.flush();
            DocumentDetail d = detail(doc);
            audit.record("NEW_VERSION", "DOCUMENT", documentId, null, VersionDto.from(v), null);
            return d;
        } finally {
            deleteQuietly(temp);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentSummary> search(UUID ownerExpertId, String typeCode, String status, String q, Pageable pageable) {
        CurrentUser user = SecurityUtils.currentUser();
        UUID restrictOwner = readAll(user) ? ownerExpertId
                : ownership.expertIdOf(user.id()).orElseThrow(() -> ApiException.forbidden("Tài khoản chưa gắn với chuyên gia"));
        if (!readAll(user) && ownerExpertId != null && !ownerExpertId.equals(restrictOwner)) {
            throw ApiException.forbidden("Chỉ xem được tài liệu của mình");
        }
        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (restrictOwner != null) ps.add(cb.equal(root.get("ownerExpertId"), restrictOwner));
            if (typeCode != null && !typeCode.isBlank()) ps.add(cb.equal(root.get("type").get("code"), typeCode));
            if (q != null && !q.isBlank()) ps.add(keywordPredicate(root, query, cb, q));
            if (status != null && !status.isBlank()) {
                var sub = query.subquery(UUID.class);
                var vr = sub.from(DocumentVersion.class);
                sub.select(vr.get("id")).where(cb.equal(vr.get("id"), root.get("currentVersionId")),
                        cb.equal(vr.get("status"), status));
                ps.add(cb.exists(sub));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Document> page = documents.findAll(spec, pageable);
        Map<UUID, DocumentVersion> current = versions.findByIdIn(page.getContent().stream()
                        .map(Document::getCurrentVersionId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(DocumentVersion::getId, Function.identity()));
        Map<UUID, String> names = expertNames(page.getContent().stream().map(Document::getOwnerExpertId).toList());
        return PageResponse.of(page, d -> summary(d, current.get(d.getCurrentVersionId()), names.get(d.getOwnerExpertId())));
    }

    /**
     * Tìm theo cụm từ: mọi từ trong q đều phải xuất hiện (không phân biệt hoa thường, có dấu / không dấu)
     * ở tiêu đề, tên loại tài liệu, tên file của một phiên bản bất kỳ, hoặc họ tên / mã chuyên gia sở hữu.
     */
    private static Predicate keywordPredicate(jakarta.persistence.criteria.Root<Document> root,
                                              jakarta.persistence.criteria.CriteriaQuery<?> query,
                                              jakarta.persistence.criteria.CriteriaBuilder cb, String q) {
        List<Predicate> all = new ArrayList<>();
        for (String word : com.npcore.ems.expert.ExpertService.stripAccents(q.trim().toLowerCase(Locale.ROOT)).split("\\s+")) {
            String like = "%" + word.replace("%", "\\%").replace("_", "\\_") + "%";
            var title = cb.function("f_unaccent", String.class, cb.lower(root.get("title")));
            var typeName = cb.function("f_unaccent", String.class, cb.lower(root.get("type").get("name")));

            var fileSub = query.subquery(UUID.class);
            var v = fileSub.from(DocumentVersion.class);
            fileSub.select(v.get("id")).where(cb.equal(v.get("documentId"), root.get("id")),
                    cb.like(cb.function("f_unaccent", String.class, cb.lower(v.get("fileName"))), like, '\\'));

            var expertSub = query.subquery(UUID.class);
            var e = expertSub.from(com.npcore.ems.expert.Expert.class);
            expertSub.select(e.get("id")).where(cb.equal(e.get("id"), root.get("ownerExpertId")), cb.or(
                    cb.like(cb.function("f_unaccent", String.class, cb.lower(e.get("fullName"))), like, '\\'),
                    cb.like(cb.lower(e.get("code")), like, '\\')));

            all.add(cb.or(cb.like(title, like, '\\'), cb.like(typeName, like, '\\'), cb.exists(fileSub), cb.exists(expertSub)));
        }
        return cb.and(all.toArray(Predicate[]::new));
    }

    @Transactional(readOnly = true)
    public DocumentDetail get(UUID id) {
        return detail(loadReadable(SecurityUtils.currentUser(), id));
    }

    /** Không readOnly: có ghi audit log DOWNLOAD (transaction read-only làm PostgreSQL từ chối INSERT). */
    @Transactional
    public Download download(UUID versionId) {
        DocumentVersion v = versions.findById(versionId).orElseThrow(() -> ApiException.notFound("Phiên bản tài liệu", versionId));
        loadReadable(SecurityUtils.currentUser(), v.getDocumentId());
        audit.record("DOWNLOAD", "DOCUMENT", v.getDocumentId(), null, Map.of("versionNo", v.getVersionNo()), null);
        return new Download(v.getFileName(), v.getContentType(), v.getFileSize(), storage.get(v.getStorageKey()));
    }

    /** Người xác minh phải khác người upload (SoD). */
    @Transactional
    public void verify(UUID versionId) {
        DocumentVersion v = versions.findById(versionId).orElseThrow(() -> ApiException.notFound("Phiên bản tài liệu", versionId));
        var r = workflow.apply("DOCUMENT", "DOCUMENT_VERSION", v.getId(), v.getStatus(), "VERIFY", null,
                Map.of("UPLOADER", v.getUploadedBy()));
        v.setStatus(r.toStatus());
        v.setVerifiedBy(SecurityUtils.currentUser().id());
        v.setVerifiedAt(OffsetDateTime.now());
        audit.record("VERIFY", "DOCUMENT", v.getDocumentId(), r.fromStatus(), r.toStatus(), null);
    }

    @Transactional
    public void reject(UUID versionId, String reason) {
        DocumentVersion v = versions.findById(versionId).orElseThrow(() -> ApiException.notFound("Phiên bản tài liệu", versionId));
        var r = workflow.apply("DOCUMENT", "DOCUMENT_VERSION", v.getId(), v.getStatus(), "REJECT", reason, Map.of());
        v.setStatus(r.toStatus());
        v.setRejectReason(reason);
        audit.record("REJECT", "DOCUMENT", v.getDocumentId(), r.fromStatus(), r.toStatus(), reason);
    }

    @Transactional
    public LinkDto addLink(UUID documentId, DocumentDtos.LinkRequest req) {
        CurrentUser user = SecurityUtils.currentUser();
        Document doc = loadReadable(user, documentId);
        resolveOwnerForWrite(user, doc.getOwnerExpertId());
        validateLinkType(req.objectType(), req.objectId());
        if (links.existsByDocumentIdAndObjectTypeAndObjectId(documentId, req.objectType(), req.objectId())) {
            throw ApiException.duplicate("Tài liệu đã được liên kết với đối tượng này");
        }
        return LinkDto.from(link(documentId, req.objectType(), req.objectId(), req.purpose()));
    }

    @Transactional
    public void removeLink(UUID linkId) {
        CurrentUser user = SecurityUtils.currentUser();
        DocumentLink l = links.findById(linkId).orElseThrow(() -> ApiException.notFound("Liên kết", linkId));
        Document doc = loadReadable(user, l.getDocumentId());
        resolveOwnerForWrite(user, doc.getOwnerExpertId());
        links.delete(l);
        audit.record("UNLINK", "DOCUMENT", l.getDocumentId(), LinkDto.from(l), null, null);
    }

    // ------------------------------------------------------------------

    private DocumentLink link(UUID documentId, String type, UUID objectId, String purpose) {
        if (links.existsByDocumentIdAndObjectTypeAndObjectId(documentId, type, objectId)) {
            return links.findByDocumentIdOrderByLinkedAtDesc(documentId).stream()
                    .filter(l -> l.getObjectType().equals(type) && l.getObjectId().equals(objectId)).findFirst().orElseThrow();
        }
        DocumentLink l = new DocumentLink();
        l.setDocumentId(documentId);
        l.setObjectType(type);
        l.setObjectId(objectId);
        l.setPurpose(purpose);
        l.setLinkedAt(OffsetDateTime.now());
        l.setLinkedBy(SecurityUtils.currentUser().id());
        links.save(l);
        audit.record("LINK", "DOCUMENT", documentId, null, Map.of("objectType", type, "objectId", objectId), null);
        return l;
    }

    private DocumentVersion storeVersion(Document doc, int no, MultipartFile file, Path temp, String sha,
                                         LocalDate issued, LocalDate expiry, CurrentUser user) {
        String original = file.getOriginalFilename() == null ? "file" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        String key = DateTimeFormatter.ofPattern("yyyy/MM").format(LocalDate.now()) + "/" + doc.getId() + "/v" + no
                + "-" + sha.substring(0, 12) + ext.replaceAll("[^a-z0-9.]", "");
        storage.put(key, temp, contentType(file));
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(doc.getId());
        v.setVersionNo(no);
        v.setFileName(original);
        v.setContentType(contentType(file));
        v.setFileSize(file.getSize());
        v.setStorageBucket(storage.bucket());
        v.setStorageKey(key);
        v.setSha256(sha);
        v.setIssuedDate(issued);
        v.setExpiryDate(expiry);
        v.setUploadedBy(user.id());
        v.setUploadedAt(OffsetDateTime.now());
        v.setStatus(doc.getType().isRequiresVerification() ? "PENDING_VERIFICATION" : "VERIFIED");
        return versions.saveAndFlush(v);
    }

    private static boolean readAll(CurrentUser u) {
        return u.hasAll("DOCUMENT_MANAGE") || u.hasAll("DOCUMENT_VERIFY") || u.hasAll("EXPERT_VIEW");
    }

    private boolean canRead(CurrentUser u, Document doc) {
        if (readAll(u)) return true;
        return doc.getOwnerExpertId() != null && ownership.expertIdOf(u.id()).map(doc.getOwnerExpertId()::equals).orElse(false);
    }

    private Document loadReadable(CurrentUser u, UUID id) {
        Document doc = documents.findById(id).orElseThrow(() -> ApiException.notFound("Tài liệu", id));
        if (!canRead(u, doc)) throw ApiException.notFound("Tài liệu", id);       // không lộ sự tồn tại
        return doc;
    }

    /** Ghi: cần DOCUMENT_MANAGE; phạm vi OWN thì chỉ được ghi cho hồ sơ của chính mình. */
    private UUID resolveOwnerForWrite(CurrentUser u, UUID requestedOwner) {
        if (!u.has("DOCUMENT_MANAGE")) throw ApiException.forbidden("Thiếu quyền DOCUMENT_MANAGE");
        if (u.hasAll("DOCUMENT_MANAGE")) return requestedOwner;
        UUID own = ownership.expertIdOf(u.id()).orElseThrow(() -> ApiException.forbidden("Tài khoản chưa gắn với chuyên gia"));
        if (requestedOwner != null && !requestedOwner.equals(own)) throw ApiException.forbidden("Chỉ được thao tác trên hồ sơ của mình");
        return own;
    }

    private static void validateLinkType(String type, UUID id) {
        if (type == null && id == null) return;
        if (type == null || id == null || !LINK_TYPES.contains(type)) {
            throw ApiException.badRequest("Liên kết không hợp lệ (objectType + objectId)");
        }
    }

    private static void validateDates(DocumentType type, LocalDate issued, LocalDate expiry) {
        if (type.isRequiresExpiry() && expiry == null) {
            throw ApiException.badRequest("Loại tài liệu '" + type.getName() + "' bắt buộc ngày hết hạn");  // BR-2.5.1
        }
        if (issued != null && expiry != null && expiry.isBefore(issued)) {
            throw ApiException.badRequest("Ngày hết hạn phải sau ngày cấp");
        }
    }

    private DocumentDetail detail(Document d) {
        List<VersionDto> vs = versions.findByDocumentIdOrderByVersionNoDesc(d.getId()).stream().map(VersionDto::from).toList();
        VersionDto current = vs.stream().filter(v -> v.id().equals(d.getCurrentVersionId())).findFirst().orElse(null);
        String owner = expertNames(java.util.Collections.singletonList(d.getOwnerExpertId())).get(d.getOwnerExpertId());
        return new DocumentDetail(d.getId(), d.getTitle(), d.getType().getCode(), d.getType().getName(),
                d.getOwnerExpertId(), owner, current, d.getStatus(), d.getCreatedAt(), vs,
                links.findByDocumentIdOrderByLinkedAtDesc(d.getId()).stream().map(LinkDto::from).toList());
    }

    private static DocumentSummary summary(Document d, DocumentVersion current, String ownerName) {
        return new DocumentSummary(d.getId(), d.getTitle(), d.getType().getCode(), d.getType().getName(),
                d.getOwnerExpertId(), ownerName, VersionDto.from(current), d.getStatus(), d.getCreatedAt());
    }

    private Map<UUID, String> expertNames(List<UUID> ids) {
        List<UUID> nonNull = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        // HashMap (không dùng Map.of): caller tra cả khoá null khi tài liệu không có chủ sở hữu
        if (nonNull.isEmpty()) return new java.util.HashMap<>();
        Map<UUID, String> out = new java.util.HashMap<>();
        jdbc.query("select expert_id, full_name from experts where expert_id in (:ids)",
                new MapSqlParameterSource("ids", nonNull),
                rs -> { out.put(rs.getObject(1, UUID.class), rs.getString(2)); });
        return out;
    }

    private static String contentType(MultipartFile f) {
        return f.getContentType() == null || f.getContentType().isBlank() ? "application/octet-stream" : f.getContentType();
    }

    private static Path toTempFile(MultipartFile file) {
        try {
            Path temp = Files.createTempFile("ems-upload-", ".bin");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return temp;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sha256(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file);
                 OutputStream sink = new DigestOutputStream(OutputStream.nullOutputStream(), md)) {
                in.transferTo(sink);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // file tạm, bỏ qua
        }
    }
}
