package com.npcore.ems.competency;

import com.npcore.ems.competency.CompetencyDtos.CompetencyActionRequest;
import com.npcore.ems.competency.CompetencyDtos.EvidenceDto;
import com.npcore.ems.competency.CompetencyDtos.EvidenceRequest;
import com.npcore.ems.competency.CompetencyDtos.ExpertCompetencyDto;
import com.npcore.ems.competency.CompetencyDtos.ExpertCompetencyRequest;
import com.npcore.ems.competency.CompetencyDtos.MatrixCell;
import com.npcore.ems.competency.CompetencyDtos.MatrixRow;
import com.npcore.ems.expert.Expert;
import com.npcore.ems.expert.ExpertAccess;
import com.npcore.ems.expert.ExpertRepository;
import com.npcore.ems.identity.User;
import com.npcore.ems.identity.UserRepository;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.security.CurrentUser;
import com.npcore.ems.shared.security.SecurityUtils;
import com.npcore.ems.shared.web.ApiException;
import com.npcore.ems.shared.web.PageResponse;
import com.npcore.ems.shared.workflow.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.npcore.ems.masterdata.AssessmentRole;
import com.npcore.ems.masterdata.AssessmentRoleRepository;
import com.npcore.ems.masterdata.Code;
import com.npcore.ems.masterdata.CodeRepository;
import com.npcore.ems.masterdata.Standard;
import com.npcore.ems.masterdata.StandardRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpertCompetencyService {

    private final ExpertCompetencyRepository repository;
    private final CompetencyDefinitionRepository definitions;
    private final CompetencyEvidenceRepository evidences;
    private final CompetencyDefinitionService definitionService;
    private final ExpertRepository experts;
    private final UserRepository users;
    private final StandardRepository standards;
    private final AssessmentRoleRepository assessmentRoles;
    private final CodeRepository codes;
    private final ObjectMapper objectMapper;
    private final ExpertAccess access;
    private final WorkflowService workflow;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<ExpertCompetencyDto> getForExpert(UUID expertId, String status, Pageable pageable) {
        access.requireView(expertId);
        Page<ExpertCompetency> page = repository.findByExpertIdAndStatusWithDetails(expertId, status, pageable);
        return PageResponse.of(page, this::toDto);
    }

    @Transactional(readOnly = true)
    public ExpertCompetencyDto getById(UUID id) {
        ExpertCompetency ec = repository.findByIdWithDetails(id)
                .orElseThrow(() -> ApiException.notFound("ExpertCompetency", id));
        access.requireView(ec.getExpert().getId());
        return toDto(ec);
    }

    /**
     * Đăng ký năng lực cho chuyên gia: BẮT BUỘC chọn từ danh mục định nghĩa năng lực đang hiệu lực
     * (theo id, hoặc theo tổ hợp tiêu chuẩn + code + vai trò). Không tự tạo định nghĩa mới.
     */
    @Transactional
    public ExpertCompetencyDto add(UUID expertId, ExpertCompetencyRequest req) {
        Expert expert = access.requireCompetencyManage(expertId);
        if ("INACTIVE".equals(expert.getStatus())) {
            throw ApiException.businessRule("Chuyên gia đã ngừng hoạt động – không đăng ký năng lực mới");
        }
        CompetencyDefinition def;
        if (req.competencyDefinitionId() != null) {
            def = definitions.findById(req.competencyDefinitionId())
                    .orElseThrow(() -> ApiException.notFound("Định nghĩa năng lực", req.competencyDefinitionId()));
        } else if (req.standardId() != null && req.assessmentRoleId() != null) {
            def = definitions.findActiveFor(req.standardId(), req.codeId(), req.assessmentRoleId()).stream().findFirst()
                    .orElseThrow(() -> ApiException.businessRule(
                            "Chưa có định nghĩa năng lực cho tổ hợp này – hãy tạo ở mục Năng lực → Định nghĩa năng lực"));
        } else {
            throw ApiException.badRequest("Chọn định nghĩa năng lực");
        }

        if (!"ACTIVE".equals(def.getStatus())) {
            throw ApiException.businessRule("Không thể đăng ký năng lực có định nghĩa chưa kích hoạt hoặc đã ngừng");
        }

        // Kiểm tra xem đã có bản ghi nào cùng definition đang chờ duyệt hoặc đang có hiệu lực chưa
        boolean existsPending = repository.existsByExpertIdAndCompetencyDefinitionIdAndStatusIn(
                expertId, def.getId(), List.of("DRAFT", "SUBMITTED", "UNDER_REVIEW", "NEED_REVISION"));
        if (existsPending) {
            throw ApiException.conflict("Chuyên gia đã có một hồ sơ năng lực này đang trong quy trình phê duyệt");
        }

        CurrentUser user = SecurityUtils.currentUser();
        ExpertCompetency ec = new ExpertCompetency();
        ec.setExpert(expert);
        ec.setCompetencyDefinition(def);
        ec.setStandardVersionId(req.standardVersionId());
        ec.setCompetencyLevel(req.competencyLevel() != null ? req.competencyLevel() : "QUALIFIED");
        ec.setStatus("DRAFT");
        ec.setRevisionNo(1);
        ec.setEffectiveFrom(req.effectiveFrom());
        ec.setEffectiveTo(req.effectiveTo());
        ec.setNotes(req.notes());
        ec.setCreatedBy(user.id());

        ExpertCompetency saved = repository.save(ec);
        audit.record("CREATE", "COMPETENCY", saved.getId(), null, toDto(saved), "Tạo hồ sơ năng lực dự thảo");
        return toDto(saved);
    }

    @Transactional
    public ExpertCompetencyDto transition(UUID id, CompetencyActionRequest req) {
        ExpertCompetency ec = repository.findByIdWithDetails(id)
                .orElseThrow(() -> ApiException.notFound("ExpertCompetency", id));
        access.requireView(ec.getExpert().getId());
        CurrentUser user = SecurityUtils.currentUser();

        Map<String, UUID> actors = new HashMap<>();
        if (ec.getSubmittedBy() != null) {
            actors.put("SUBMITTER", ec.getSubmittedBy());
        } else if (ec.getCreatedBy() != null) {
            actors.put("SUBMITTER", ec.getCreatedBy());
        }

        WorkflowService.Result result = workflow.apply(
                "COMPETENCY", "COMPETENCY", ec.getId(), ec.getStatus(), req.action(), req.comment(), actors);

        String oldStatus = ec.getStatus();
        ec.setStatus(result.toStatus());

        switch (req.action()) {
            case "SUBMIT", "RESUBMIT" -> {
                ec.setSubmittedBy(user.id());
                ec.setSubmittedAt(OffsetDateTime.now());
            }
            case "APPROVE" -> {
                ec.setApprovedBy(user.id());
                ec.setApprovedAt(OffsetDateTime.now());
                if (ec.getEffectiveFrom() == null) {
                    ec.setEffectiveFrom(LocalDate.now());
                }
                if (ec.getFirstApprovedDate() == null) {
                    ec.setFirstApprovedDate(LocalDate.now());
                }
                Short validityMonths = ec.getCompetencyDefinition().getDefaultValidityMonths();
                if (ec.getEffectiveTo() == null && validityMonths != null && validityMonths > 0) {
                    ec.setEffectiveTo(ec.getEffectiveFrom().plusMonths(validityMonths));
                }
                // Nếu đây là revision mới thay thế bản cũ, đánh dấu bản cũ SUPERSEDED
                if (ec.getSupersedesId() != null) {
                    repository.findById(ec.getSupersedesId()).ifPresent(oldEc -> {
                        oldEc.setStatus("SUPERSEDED");
                        repository.save(oldEc);
                    });
                }
            }
            case "REVOKE" -> {
                if (req.comment() != null) {
                    ec.setNotes((ec.getNotes() != null ? ec.getNotes() + "\n" : "") + "Lý do thu hồi: " + req.comment());
                }
            }
        }

        ExpertCompetency saved = repository.save(ec);
        audit.record(req.action(), "COMPETENCY", id, oldStatus, saved.getStatus(), req.comment());
        return toDto(saved);
    }

    @Transactional
    public EvidenceDto addEvidence(UUID expertCompetencyId, EvidenceRequest req) {
        ExpertCompetency ec = repository.findById(expertCompetencyId)
                .orElseThrow(() -> ApiException.notFound("ExpertCompetency", expertCompetencyId));
        access.requireCompetencyManage(ec.getExpert().getId());

        if (!List.of("DRAFT", "NEED_REVISION").contains(ec.getStatus())) {
            throw ApiException.businessRule("Chỉ có thể bổ sung bằng chứng khi hồ sơ năng lực ở trạng thái DRAFT hoặc NEED_REVISION");
        }

        CompetencyEvidence e = new CompetencyEvidence();
        e.setExpertCompetency(ec);
        e.setDocumentId(req.documentId());
        e.setEvidenceType(req.evidenceType());
        e.setSourceObjectType(req.sourceObjectType());
        e.setSourceObjectId(req.sourceObjectId());
        e.setDescription(req.description());

        CompetencyEvidence saved = evidences.save(e);
        audit.record("ADD_EVIDENCE", "COMPETENCY", expertCompetencyId, null, saved.getId(), "Thêm minh chứng năng lực: " + req.evidenceType());
        return toEvidenceDto(saved);
    }

    @Transactional
    public void removeEvidence(UUID expertCompetencyId, UUID evidenceId) {
        CompetencyEvidence e = evidences.findById(evidenceId)
                .orElseThrow(() -> ApiException.notFound("CompetencyEvidence", evidenceId));
        ExpertCompetency ec = e.getExpertCompetency();
        access.requireCompetencyManage(ec.getExpert().getId());

        if (!List.of("DRAFT", "NEED_REVISION").contains(ec.getStatus())) {
            throw ApiException.businessRule("Không thể xóa bằng chứng khi hồ sơ đã nộp hoặc đã phê duyệt");
        }

        evidences.delete(e);
        audit.record("REMOVE_EVIDENCE", "COMPETENCY", expertCompetencyId, evidenceId, null, "Xóa minh chứng năng lực");
    }

    /**
     * Ma trận năng lực của MỘT tiêu chuẩn: cột = năng lực toàn tiêu chuẩn ("*") + các code có định nghĩa năng lực;
     * dòng = chuyên gia (trừ Ngừng hoạt động) có ít nhất một năng lực đã duyệt.
     * Scheme bật "code cha bao code con" → code con được hiển thị là kế thừa từ code cha.
     */
    @Transactional(readOnly = true)
    public CompetencyDtos.MatrixResponse getMatrix(UUID standardId, UUID roleId, boolean includeExpired) {
        Standard std = standards.findById(standardId).orElseThrow(() -> ApiException.notFound("Tiêu chuẩn", standardId));
        boolean parentCovers = std.getScheme().isParentCoversChild();
        LocalDate today = LocalDate.now();

        // Cột: code có định nghĩa năng lực đang hiệu lực cho tiêu chuẩn (sắp theo path), "*" đầu tiên
        List<CompetencyDefinition> defs = definitions.findByStandardIdWithDetails(standardId, "ACTIVE");
        Map<UUID, Code> codeById = new java.util.TreeMap<>();
        Map<String, Code> byPath = new java.util.TreeMap<>();
        for (CompetencyDefinition d : defs) if (d.getCode() != null) byPath.put(d.getCode().getPath() + " " + d.getCode().getId(), d.getCode());   // cha trước con
        List<CompetencyDtos.MatrixColumn> columns = new ArrayList<>();
        columns.add(new CompetencyDtos.MatrixColumn(null, "*", "Toàn tiêu chuẩn", null));
        Map<UUID, String> valueById = new HashMap<>();
        byPath.values().forEach(c -> valueById.put(c.getId(), c.getValue()));
        for (Code c : byPath.values()) {
            String parent = c.getParentId() == null ? null
                    : valueById.getOrDefault(c.getParentId(), codes.findById(c.getParentId()).map(Code::getValue).orElse(null));
            columns.add(new CompetencyDtos.MatrixColumn(c.getId(), c.getValue(), c.getName(), parent));
        }

        Map<UUID, MatrixRow> rowMap = new java.util.LinkedHashMap<>();
        for (ExpertCompetency ec : repository.findApprovedByStandardId(standardId)) {
            CompetencyDefinition cd = ec.getCompetencyDefinition();
            if (roleId != null && !roleId.equals(cd.getAssessmentRole().getId())) continue;
            Expert exp = ec.getExpert();
            if ("INACTIVE".equals(exp.getStatus()) || exp.getDeletedAt() != null) continue;
            boolean expired = ec.getEffectiveTo() != null && ec.getEffectiveTo().isBefore(today);
            if (expired && !includeExpired) continue;
            boolean soon = !expired && ec.getEffectiveTo() != null && !ec.getEffectiveTo().isAfter(today.plusDays(60));
            MatrixRow row = rowMap.computeIfAbsent(exp.getId(), k -> new MatrixRow(
                    exp.getId(), exp.getCode(), exp.getFullName(), exp.getExpertType(), exp.getEmploymentType(),
                    exp.getStatus(), exp.getSuspendedUntil(), new ArrayList<>()));
            row.cells().add(new MatrixCell(ec.getId(), cd.getId(), cd.getStandard().getCode(),
                    cd.getCode() != null ? cd.getCode().getValue() : "*", cd.getAssessmentRole().getCode(),
                    ec.getCompetencyLevel(), ec.getStatus(), ec.getEffectiveFrom(), ec.getEffectiveTo(), expired, soon, false));
        }

        if (parentCovers) {
            for (MatrixRow row : rowMap.values()) {
                List<MatrixCell> extra = new ArrayList<>();
                for (CompetencyDtos.MatrixColumn col : columns) {
                    if (col.parentCode() == null) continue;
                    boolean direct = row.cells().stream().anyMatch(c -> c.codeValue().equals(col.codeValue()));
                    if (direct) continue;
                    row.cells().stream().filter(c -> c.codeValue().equals(col.parentCode()) && !c.inherited())
                            .forEach(c -> extra.add(new MatrixCell(c.competencyId(), c.definitionId(), c.standardCode(),
                                    col.codeValue(), c.roleCode(), c.level(), c.status(), c.effectiveFrom(), c.effectiveTo(),
                                    c.expired(), c.expiringSoon(), true)));
                }
                row.cells().addAll(extra);
            }
        }
        List<MatrixRow> rows = new ArrayList<>(rowMap.values());
        rows.sort(java.util.Comparator.comparing(MatrixRow::expertName));
        return new CompetencyDtos.MatrixResponse(std.getId(), std.getCode(), std.getName(), parentCovers, columns, rows);
    }

    public ExpertCompetencyDto toDto(ExpertCompetency ec) {
        List<String> actions = workflow.availableActions("COMPETENCY", ec.getStatus());
        List<CompetencyEvidence> evList = evidences.findByExpertCompetencyId(ec.getId());
        List<EvidenceDto> evDtos = evList.stream().map(this::toEvidenceDto).toList();

        return new ExpertCompetencyDto(
                ec.getId(),
                ec.getExpert().getId(),
                definitionService.toDto(ec.getCompetencyDefinition()),
                ec.getStandardVersionId(),
                ec.getCompetencyLevel(),
                ec.getStatus(),
                ec.getRevisionNo(),
                ec.getEffectiveFrom(),
                ec.getEffectiveTo(),
                ec.getFirstApprovedDate(),
                ec.getApprovedBy(),
                ec.getApprovedAt(),
                ec.getSubmittedBy(),
                ec.getSubmittedAt(),
                ec.getNotes(),
                ec.getCreatedAt(),
                actions,
                evDtos);
    }

    private EvidenceDto toEvidenceDto(CompetencyEvidence e) {
        return new EvidenceDto(
                e.getId(),
                e.getExpertCompetency().getId(),
                e.getDocumentId(),
                e.getEvidenceType(),
                e.getSourceObjectType(),
                e.getSourceObjectId(),
                e.getDescription(),
                e.getCreatedAt());
    }
}
