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

    @Transactional
    public ExpertCompetencyDto add(UUID expertId, ExpertCompetencyRequest req) {
        Expert expert = access.requireEdit(expertId);
        CompetencyDefinition def;
        if (req.competencyDefinitionId() != null) {
            def = definitions.findById(req.competencyDefinitionId())
                    .orElseThrow(() -> ApiException.notFound("CompetencyDefinition", req.competencyDefinitionId()));
        } else if (req.standardId() != null && req.assessmentRoleId() != null) {
            Standard std = standards.findById(req.standardId())
                    .orElseThrow(() -> ApiException.notFound("Standard", req.standardId()));
            AssessmentRole role = assessmentRoles.findById(req.assessmentRoleId())
                    .orElseThrow(() -> ApiException.notFound("AssessmentRole", req.assessmentRoleId()));
            Code code = req.codeId() != null ? codes.findById(req.codeId()).orElse(null) : null;

            UUID codeId = code != null ? code.getId() : null;
            def = definitions.findAll().stream()
                    .filter(d -> d.getStandard().getId().equals(std.getId())
                            && Objects.equals(d.getCode() != null ? d.getCode().getId() : null, codeId)
                            && d.getAssessmentRole().getId().equals(role.getId())
                            && !"RETIRED".equals(d.getStatus()))
                    .findFirst()
                    .orElseGet(() -> {
                        CompetencyDefinition cd = new CompetencyDefinition();
                        cd.setScheme(std.getScheme());
                        cd.setStandard(std);
                        cd.setCode(code);
                        cd.setAssessmentRole(role);
                        cd.setDefaultValidityMonths((short) 36);
                        cd.setEffectiveFrom(LocalDate.now());
                        cd.setStatus("ACTIVE");
                        cd.setVersion("1.0");
                        cd.setCriteria(objectMapper.createObjectNode());
                        return definitions.save(cd);
                    });
        } else {
            throw ApiException.badRequest("Cần chọn Tiêu chuẩn và Vai trò đánh giá");
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
        access.requireEdit(ec.getExpert().getId());

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
        access.requireEdit(ec.getExpert().getId());

        if (!List.of("DRAFT", "NEED_REVISION").contains(ec.getStatus())) {
            throw ApiException.businessRule("Không thể xóa bằng chứng khi hồ sơ đã nộp hoặc đã phê duyệt");
        }

        evidences.delete(e);
        audit.record("REMOVE_EVIDENCE", "COMPETENCY", expertCompetencyId, evidenceId, null, "Xóa minh chứng năng lực");
    }

    @Transactional(readOnly = true)
    public List<MatrixRow> getMatrix(UUID standardId) {
        if (standardId == null) {
            return Collections.emptyList();
        }
        List<ExpertCompetency> list = repository.findApprovedByStandardId(standardId);

        Map<UUID, MatrixRow> rowMap = new HashMap<>();
        for (ExpertCompetency ec : list) {
            Expert exp = ec.getExpert();
            MatrixRow row = rowMap.computeIfAbsent(exp.getId(), k -> new MatrixRow(
                    exp.getId(), exp.getCode(), exp.getFullName(), exp.getExpertType(),
                    exp.getEmploymentType(), new ArrayList<>()));

            CompetencyDefinition cd = ec.getCompetencyDefinition();
            row.cells().add(new MatrixCell(
                    ec.getId(),
                    cd.getId(),
                    cd.getStandard().getCode(),
                    cd.getCode() != null ? cd.getCode().getValue() : "*",
                    cd.getAssessmentRole().getCode(),
                    ec.getCompetencyLevel(),
                    ec.getStatus(),
                    ec.getEffectiveFrom(),
                    ec.getEffectiveTo()));
        }
        return new ArrayList<>(rowMap.values());
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
