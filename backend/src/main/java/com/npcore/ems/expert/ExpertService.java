package com.npcore.ems.expert;

import com.npcore.ems.document.DocumentRepository;
import com.npcore.ems.expert.ExpertDtos.Counts;
import com.npcore.ems.expert.ExpertDtos.ExpertDetail;
import com.npcore.ems.expert.ExpertDtos.ExpertRequest;
import com.npcore.ems.expert.ExpertDtos.ExpertSummary;
import com.npcore.ems.expert.ExpertDtos.HistoryEntry;
import com.npcore.ems.identity.DepartmentRepository;
import com.npcore.ems.identity.UserRepository;
import com.npcore.ems.masterdata.LocationRepository;
import com.npcore.ems.shared.audit.AuditLog;
import com.npcore.ems.shared.audit.AuditLogRepository;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.security.CurrentUser;
import com.npcore.ems.shared.security.SecurityUtils;
import com.npcore.ems.shared.web.ApiException;
import com.npcore.ems.shared.web.PageResponse;
import com.npcore.ems.shared.workflow.ApprovalHistory;
import com.npcore.ems.shared.workflow.WorkflowService;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpertService {

    static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExpertRepository experts;
    private final ExpertCodeSequenceRepository sequences;
    private final ExpertEducationRepository educations;
    private final ExpertExperienceRepository experiences;
    private final ExpertTrainingRepository trainings;
    private final ExpertCertificateRepository certificates;
    private final DocumentRepository documents;
    private final DepartmentRepository departments;
    private final LocationRepository locations;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final ExpertAccess access;
    private final WorkflowService workflow;
    private final AuditService audit;

    // ------------------------------------------------------------ search (FR-5.1, server-side)

    @Transactional(readOnly = true)
    public PageResponse<ExpertSummary> search(String q, String expertType, String employmentType, String status,
                                              UUID departmentId, Pageable pageable) {
        if (!SecurityUtils.currentUser().hasAll("EXPERT_VIEW")) {
            throw ApiException.forbidden("Chỉ xem được hồ sơ của mình (dùng /experts/me)");
        }
        Specification<Expert> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedAt")));
            if (q != null && !q.isBlank()) {
                String needle = "%" + stripAccents(q.trim().toLowerCase(Locale.ROOT)) + "%";
                Expression<String> name = cb.function("f_unaccent", String.class, cb.lower(root.get("fullName")));
                ps.add(cb.or(cb.like(name, needle), cb.like(cb.lower(root.get("code")), needle),
                        cb.like(cb.lower(root.get("email")), needle)));
            }
            if (expertType != null && !expertType.isBlank()) {
                // CGĐG gồm cả BOTH; CGKT gồm cả BOTH
                ps.add(root.get("expertType").in(expertType, "BOTH"));
            }
            if (employmentType != null && !employmentType.isBlank()) ps.add(cb.equal(root.get("employmentType"), employmentType));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            if (departmentId != null) ps.add(cb.equal(root.get("departmentId"), departmentId));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Expert> page = experts.findAll(spec, pageable);
        Map<UUID, String> deptNames = departmentNames(page.getContent().stream().map(Expert::getDepartmentId).collect(Collectors.toSet()));
        return PageResponse.of(page, e -> new ExpertSummary(e.getId(), e.getCode(), e.getFullName(), e.getExpertType(),
                e.getEmploymentType(), e.getStatus(), deptNames.get(e.getDepartmentId()), e.getEmail(), e.getPhone(),
                e.getUpdatedAt(), e.getSuspendedUntil()));
    }

    // ------------------------------------------------------------ CRUD

    @Transactional
    public ExpertDetail create(ExpertRequest r) {
        SecurityUtils.require("EXPERT_CREATE");
        return create(r, null);
    }

    /**
     * Dùng chung cho tạo tay và import; expertCode != null khi import giữ mã cũ.
     * noRollbackFor: lỗi nghiệp vụ của một dòng import không được làm hỏng cả transaction import.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public ExpertDetail create(ExpertRequest r, String expertCode) {
        Expert e = new Expert();
        if (expertCode != null) {
            if (experts.existsByCode(expertCode)) throw ApiException.duplicate("Mã chuyên gia đã tồn tại: " + expertCode);
            e.setCode(expertCode);
        } else {
            e.setCode(nextCode(r.employmentType()));
        }
        apply(e, r, true);
        e.setCreatedBy(SecurityUtils.currentUser().id());
        experts.saveAndFlush(e);
        ExpertDetail d = detail(e);
        audit.record("CREATE", "EXPERT", e.getId(), null, d, null);
        return d;
    }

    @Transactional(readOnly = true)
    public ExpertDetail get(UUID id) {
        return detail(access.requireView(id));
    }

    @Transactional(readOnly = true)
    public ExpertDetail me() {
        CurrentUser u = SecurityUtils.currentUser();
        Expert e = experts.findByUserId(u.id()).orElseThrow(() -> ApiException.notFound("Hồ sơ chuyên gia của tài khoản", u.username()));
        return detail(access.requireView(e.getId()));
    }

    @Transactional
    public ExpertDetail update(UUID id, ExpertRequest r) {
        boolean general = SecurityUtils.currentUser().has("EXPERT_EDIT");
        Expert e = general ? access.requireEdit(id) : access.requireContactEdit(id);
        ExpertDetail before = detail(e);
        if (general) {
            apply(e, r, access.editAll());
        } else {                                   // chuyên gia: chỉ thông tin liên hệ
            e.setPhone(blank(r.phone()));
            e.setEmail(blank(r.email()));
            e.setAddress(blank(r.address()));
        }
        e.setUpdatedBy(SecurityUtils.currentUser().id());
        experts.flush();
        ExpertDetail after = detail(e);
        audit.record("UPDATE", "EXPERT", id, before, after, null);
        return after;
    }

    /**
     * Chuyển trạng thái theo workflow EXPERT (cấu hình trong DB, trigger DB kiểm tra lại).
     * SUBMIT: NV hồ sơ trình (hồ sơ phải đủ tối thiểu). REVIEW / RETURN: Chuyên gia trưởng thẩm tra.
     * APPROVE / RETURN: GĐCN. Người trình không tự thẩm tra / tự phê duyệt.
     * SUSPEND có thể kèm suspendedUntil (ngày cuối bị dừng) – hết hạn hệ thống tự mở lại.
     */
    @Transactional
    public ExpertDetail changeStatus(UUID id, String action, String comment, LocalDate suspendedUntil) {
        Expert e = access.requireView(id);
        if (!SecurityUtils.currentUser().hasAll("EXPERT_VIEW")) throw ApiException.forbidden("Không được tự đổi trạng thái");
        if ("SUSPEND".equals(action) && (comment == null || comment.isBlank())) {
            throw ApiException.badRequest("Thao tác SUSPEND bắt buộc nhập lý do");
        }
        if (suspendedUntil != null) {
            if (!"SUSPEND".equals(action)) throw ApiException.badRequest("Chỉ nhập thời hạn khi dừng đánh giá");
            if (!suspendedUntil.isAfter(LocalDate.now())) throw ApiException.badRequest("Ngày dừng đến phải sau hôm nay");
        }
        Map<String, UUID> actors = new HashMap<>();
        if ("APPROVE".equals(action) || "REVIEW".equals(action)) {
            workflow.history("EXPERT", id).stream().filter(h -> "SUBMIT".equals(h.getAction())).findFirst()
                    .ifPresent(h -> actors.put("SUBMITTER", h.getActorId()));
        }
        String note = suspendedUntil == null ? comment
                : (comment == null ? "" : comment + " ") + "(dừng đến hết " + suspendedUntil.format(DMY) + ")";
        var result = workflow.apply("EXPERT", "EXPERT", id, e.getStatus(), action, note, actors);   // kiểm quyền trước
        if ("SUBMIT".equals(action)) requireComplete(e);           // lỗi → rollback cả lịch sử vừa ghi
        e.setSuspendedUntil("SUSPEND".equals(action) ? suspendedUntil : null);
        e.setStatus(result.toStatus());
        e.setStatusReason(note);
        e.setUpdatedBy(SecurityUtils.currentUser().id());
        experts.flush();
        audit.record(action, "EXPERT", id, result.fromStatus(), result.toStatus(), note);
        return detail(e);
    }

    /** Điều kiện tối thiểu để nộp hồ sơ. */
    private void requireComplete(Expert e) {
        List<String> missing = new ArrayList<>();
        if (e.getDateOfBirth() == null) missing.add("ngày sinh");
        if (e.getPhone() == null) missing.add("số điện thoại");
        if (educations.countByExpertId(e.getId()) == 0) missing.add("ít nhất 1 học vấn");
        if (experiences.countByExpertId(e.getId()) == 0) missing.add("ít nhất 1 kinh nghiệm");
        if (!missing.isEmpty()) {
            throw ApiException.businessRule("Hồ sơ chưa đủ để nộp, còn thiếu: " + String.join(", ", missing));
        }
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID id) {
        access.requireView(id);
        List<ApprovalHistory> approvals = workflow.history("EXPERT", id);
        List<AuditLog> logs = auditLogs.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("objectType"), "EXPERT"), cb.equal(root.get("objectId"), id.toString()),
                root.get("action").in("CREATE", "UPDATE", "IMPORT")));
        Set<UUID> actorIds = approvals.stream().map(ApprovalHistory::getActorId).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(actorIds).forEach(u -> names.put(u.getId(), u.getUsername()));
        return Stream.concat(
                        approvals.stream().map(a -> new HistoryEntry(a.getCreatedAt(), names.get(a.getActorId()),
                                a.getAction(), a.getFromStatus(), a.getToStatus(), a.getComment())),
                        logs.stream().map(l -> new HistoryEntry(l.getOccurredAt(), l.getUsername(), l.getAction(),
                                null, null, l.getReason())))
                .sorted(Comparator.comparing(HistoryEntry::at).reversed())
                .toList();
    }

    // ------------------------------------------------------------ helpers

    private void apply(Expert e, ExpertRequest r, boolean adminFields) {
        e.setFullName(r.fullName().trim());
        e.setDateOfBirth(r.dateOfBirth());
        e.setGender(r.gender());
        e.setIdNumber(blank(r.idNumber()));
        e.setAddress(blank(r.address()));
        e.setPhone(blank(r.phone()));
        e.setEmail(blank(r.email()));
        e.setHomeLocationId(r.homeLocationId());
        if (r.homeLocationId() != null && !locations.existsById(r.homeLocationId())) {
            throw ApiException.notFound("Địa điểm", r.homeLocationId());
        }
        if (!adminFields) return;                       // chuyên gia tự sửa: không đổi loại, HĐ, phòng ban, tài khoản
        if (e.getId() != null && !Objects.equals(e.getEmploymentType(), r.employmentType())) {
            // BR-2.1.2: mã FT-/PT- đã cấp không đổi; chỉ ghi nhận loại HĐ mới
            audit.record("CHANGE_EMPLOYMENT_TYPE", "EXPERT", e.getId(), e.getEmploymentType(), r.employmentType(), null);
        }
        e.setExpertType(r.expertType());
        e.setEmploymentType(r.employmentType());
        if (r.departmentId() != null && !departments.existsById(r.departmentId())) {
            throw ApiException.notFound("Phòng ban", r.departmentId());
        }
        e.setDepartmentId(r.departmentId());
        e.setPosition(blank(r.position()));
        e.setJoinedDate(r.joinedDate());
        e.setMaxMandaysPerMonth(r.maxMandaysPerMonth());
        if (r.userId() != null) {
            if (!users.existsById(r.userId())) throw ApiException.notFound("Tài khoản", r.userId());
            if (e.getId() != null ? experts.userLinkedElsewhere(r.userId(), e.getId())
                    : experts.findByUserId(r.userId()).isPresent()) {
                throw ApiException.duplicate("Tài khoản đã gắn với chuyên gia khác");
            }
        }
        e.setUserId(r.userId());
    }

    /** Sinh mã theo cấu hình expert_code_sequences, khoá dòng để không trùng khi tạo đồng thời. */
    String nextCode(String employmentType) {
        ExpertCodeSequence seq = sequences.lockByEmploymentType(employmentType)
                .orElseThrow(() -> ApiException.businessRule("Chưa cấu hình mã cho loại hợp đồng " + employmentType));
        String code;
        do {
            code = seq.getPrefix() + String.format("%0" + seq.getPadLength() + "d", seq.getNextValue());
            seq.setNextValue(seq.getNextValue() + 1);
        } while (experts.existsByCode(code));                // bỏ qua mã đã có (VD import giữ mã cũ)
        return code;
    }

    ExpertDetail detail(Expert e) {
        String dept = e.getDepartmentId() == null ? null
                : departments.findById(e.getDepartmentId()).map(d -> d.getName()).orElse(null);
        String loc = e.getHomeLocationId() == null ? null
                : locations.findById(e.getHomeLocationId()).map(l -> l.getName()).orElse(null);
        String username = e.getUserId() == null ? null
                : users.findById(e.getUserId()).map(u -> u.getUsername()).orElse(null);
        Counts counts = e.getId() == null ? new Counts(0, 0, 0, 0, 0) : new Counts(
                educations.countByExpertId(e.getId()), experiences.countByExpertId(e.getId()),
                trainings.countByExpertId(e.getId()), certificates.countByExpertId(e.getId()),
                documents.countByOwnerExpertId(e.getId()));
        List<String> actions = availableActions(e);
        return new ExpertDetail(e.getId(), e.getCode(), e.getFullName(), e.getDateOfBirth(), e.getGender(),
                e.getIdNumber(), e.getAddress(), e.getPhone(), e.getEmail(), e.getExpertType(), e.getEmploymentType(),
                e.getDepartmentId(), dept, e.getPosition(), e.getJoinedDate(), e.getHomeLocationId(), loc, e.getUserId(),
                username, e.getMaxMandaysPerMonth(), e.getStatus(), e.getStatusReason(), e.getSuspendedUntil(), actions, e.getCreatedAt(),
                e.getUpdatedAt(), counts);
    }

    private List<String> availableActions(Expert e) {
        return SecurityUtils.currentUserOptional().filter(u -> u.hasAll("EXPERT_VIEW")).isPresent()
                ? workflow.availableActions("EXPERT", e.getStatus()) : List.of();
    }

    private Map<UUID, String> departmentNames(Set<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        departments.findAllById(ids.stream().filter(Objects::nonNull).toList()).forEach(d -> out.put(d.getId(), d.getName()));
        return out;
    }

    public static String stripAccents(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('đ', 'd').replace('Đ', 'D');
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
