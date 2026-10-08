package com.npcore.ems.competency;

import com.npcore.ems.competency.CompetencyDtos.CompetencyDefinitionDto;
import com.npcore.ems.competency.CompetencyDtos.CompetencyDefinitionRequest;
import com.npcore.ems.masterdata.AssessmentRole;
import com.npcore.ems.masterdata.AssessmentRoleRepository;
import com.npcore.ems.masterdata.Code;
import com.npcore.ems.masterdata.CodeRepository;
import com.npcore.ems.masterdata.Standard;
import com.npcore.ems.masterdata.StandardRepository;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.web.ApiException;
import com.npcore.ems.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompetencyDefinitionService {

    private final CompetencyDefinitionRepository repository;
    private final StandardRepository standards;
    private final CodeRepository codes;
    private final com.npcore.ems.masterdata.CodeSetRepository codeSets;
    private final AssessmentRoleRepository assessmentRoles;
    private final AuditService audit;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<CompetencyDefinitionDto> list(UUID schemeId, UUID standardId, UUID roleId, String status, String q,
                                                      Pageable pageable) {
        Specification<CompetencyDefinition> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (schemeId != null) ps.add(cb.equal(root.get("scheme").get("id"), schemeId));
            if (standardId != null) ps.add(cb.equal(root.get("standard").get("id"), standardId));
            if (roleId != null) ps.add(cb.equal(root.get("assessmentRole").get("id"), roleId));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            var code = root.join("code", jakarta.persistence.criteria.JoinType.LEFT);
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                ps.add(cb.or(cb.like(cb.lower(code.get("value")), like),
                        cb.like(cb.function("f_unaccent", String.class, cb.lower(code.get("name"))),
                                "%" + com.npcore.ems.expert.ExpertService.stripAccents(q.trim().toLowerCase(java.util.Locale.ROOT)) + "%")));
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                // Sắp xếp: tiêu chuẩn → năng lực toàn tiêu chuẩn trước → code → vai trò
                query.orderBy(cb.asc(root.get("standard").get("code")), cb.asc(cb.selectCase().when(cb.isNull(code.get("id")), 0).otherwise(1)),
                        cb.asc(code.get("path")), cb.asc(root.get("assessmentRole").get("sortOrder")));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<CompetencyDefinition> page = repository.findAll(spec,
                org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        return PageResponse.of(page, this::toDto);
    }

    @Transactional(readOnly = true)
    public List<CompetencyDefinitionDto> listAllActive(UUID standardId) {
        List<CompetencyDefinition> list = standardId == null
                ? repository.findAllWithDetails("ACTIVE")
                : repository.findByStandardIdWithDetails(standardId, "ACTIVE");
        return list.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CompetencyDefinitionDto getById(UUID id) {
        return toDto(findEntity(id));
    }

    @Transactional
    public CompetencyDefinitionDto create(CompetencyDefinitionRequest req) {
        Standard standard = standards.findById(req.standardId())
                .orElseThrow(() -> ApiException.notFound("Tiêu chuẩn", req.standardId()));
        checkScheme(req.schemeId(), standard);
        AssessmentRole role = assessmentRoles.findById(req.assessmentRoleId())
                .orElseThrow(() -> ApiException.notFound("Vai trò đánh giá", req.assessmentRoleId()));
        Code code = req.codeId() == null ? null : codeOfScheme(req.codeId(), standard);
        String version = req.version() != null && !req.version().isBlank() ? req.version().trim() : "1";
        if (repository.duplicateExists(standard.getId(), code == null ? null : code.getId(), role.getId(), version, null)) {
            throw ApiException.duplicate("Đã có định nghĩa năng lực " + label(standard, code, role) + " phiên bản " + version);
        }
        CompetencyDefinition cd = new CompetencyDefinition();
        cd.setScheme(standard.getScheme());
        cd.setStandard(standard);
        cd.setCode(code);
        cd.setAssessmentRole(role);
        cd.setDefaultValidityMonths(req.defaultValidityMonths());
        cd.setVersion(version);
        cd.setEffectiveFrom(req.effectiveFrom());
        cd.setEffectiveTo(req.effectiveTo());
        checkDates(cd);
        cd.setStatus(req.status() != null ? req.status() : "ACTIVE");
        cd.setCriteria(req.criteria() != null ? req.criteria() : objectMapper.createObjectNode());
        CompetencyDefinition saved = repository.save(cd);
        audit.record("CREATE", "COMPETENCY_DEFINITION", saved.getId(), null, toDto(saved), "Tạo định nghĩa năng lực");
        return toDto(saved);
    }

    /** Tạo hàng loạt: một tiêu chuẩn + một vai trò cho nhiều code (bỏ qua tổ hợp đã có). */
    @Transactional
    public CompetencyDtos.BulkResult createBulk(CompetencyDtos.BulkDefinitionRequest req) {
        Standard standard = standards.findById(req.standardId())
                .orElseThrow(() -> ApiException.notFound("Tiêu chuẩn", req.standardId()));
        AssessmentRole role = assessmentRoles.findById(req.assessmentRoleId())
                .orElseThrow(() -> ApiException.notFound("Vai trò đánh giá", req.assessmentRoleId()));
        String version = req.version() != null && !req.version().isBlank() ? req.version().trim() : "1";
        List<Code> targets = new ArrayList<>();
        if (req.includeGeneral()) targets.add(null);
        if (req.codeIds() != null) for (UUID id : req.codeIds()) targets.add(codeOfScheme(id, standard));
        if (targets.isEmpty()) throw ApiException.badRequest("Chọn ít nhất một code hoặc 'Toàn tiêu chuẩn'");
        int created = 0;
        int skipped = 0;
        for (Code code : targets) {
            if (repository.duplicateExists(standard.getId(), code == null ? null : code.getId(), role.getId(), version, null)) {
                skipped++;
                continue;
            }
            CompetencyDefinition cd = new CompetencyDefinition();
            cd.setScheme(standard.getScheme());
            cd.setStandard(standard);
            cd.setCode(code);
            cd.setAssessmentRole(role);
            cd.setDefaultValidityMonths(req.defaultValidityMonths());
            cd.setVersion(version);
            cd.setEffectiveFrom(req.effectiveFrom());
            cd.setStatus("ACTIVE");
            cd.setCriteria(req.criteria() != null ? req.criteria() : objectMapper.createObjectNode());
            repository.save(cd);
            created++;
        }
        audit.record("CREATE_BULK", "COMPETENCY_DEFINITION", standard.getId(), null,
                java.util.Map.of("role", role.getCode(), "created", created, "skipped", skipped), "Tạo hàng loạt định nghĩa năng lực");
        return new CompetencyDtos.BulkResult(created, skipped);
    }

    @Transactional
    public CompetencyDefinitionDto update(UUID id, CompetencyDefinitionRequest req) {
        CompetencyDefinition cd = findEntity(id);
        if ("RETIRED".equals(cd.getStatus())) {
            throw ApiException.businessRule("Không sửa được định nghĩa đã ngừng hiệu lực – hãy tạo phiên bản mới");
        }
        CompetencyDefinitionDto oldDto = toDto(cd);
        Standard standard = standards.findById(req.standardId())
                .orElseThrow(() -> ApiException.notFound("Tiêu chuẩn", req.standardId()));
        checkScheme(req.schemeId(), standard);
        AssessmentRole role = assessmentRoles.findById(req.assessmentRoleId())
                .orElseThrow(() -> ApiException.notFound("Vai trò đánh giá", req.assessmentRoleId()));
        Code code = req.codeId() == null ? null : codeOfScheme(req.codeId(), standard);
        String version = req.version() != null && !req.version().isBlank() ? req.version().trim() : cd.getVersion();
        if (repository.duplicateExists(standard.getId(), code == null ? null : code.getId(), role.getId(), version, id)) {
            throw ApiException.duplicate("Đã có định nghĩa năng lực " + label(standard, code, role) + " phiên bản " + version);
        }
        cd.setScheme(standard.getScheme());
        cd.setStandard(standard);
        cd.setCode(code);
        cd.setAssessmentRole(role);
        cd.setVersion(version);
        cd.setDefaultValidityMonths(req.defaultValidityMonths());
        if (req.effectiveFrom() != null) cd.setEffectiveFrom(req.effectiveFrom());
        cd.setEffectiveTo(req.effectiveTo());
        checkDates(cd);
        if (req.criteria() != null) cd.setCriteria(req.criteria());
        CompetencyDefinition updated = repository.save(cd);
        audit.record("UPDATE", "COMPETENCY_DEFINITION", id, oldDto, toDto(updated), "Cập nhật định nghĩa năng lực");
        return toDto(updated);
    }

    /** Scheme luôn lấy theo tiêu chuẩn; nếu client gửi scheme khác thì báo lỗi. */
    private static void checkScheme(UUID schemeId, Standard standard) {
        if (schemeId != null && !schemeId.equals(standard.getScheme().getId())) {
            throw ApiException.badRequest("Tiêu chuẩn " + standard.getCode() + " không thuộc scheme đã chọn");
        }
    }

    /** Code phải thuộc một bộ mã của đúng scheme của tiêu chuẩn. */
    private Code codeOfScheme(UUID codeId, Standard standard) {
        Code code = codes.findById(codeId).orElseThrow(() -> ApiException.notFound("Code", codeId));
        UUID codeScheme = codeSets.findById(code.getCodeSetId()).map(s -> s.getScheme().getId()).orElse(null);
        if (!standard.getScheme().getId().equals(codeScheme)) {
            throw ApiException.badRequest("Code " + code.getValue() + " không thuộc bộ mã của scheme " + standard.getScheme().getCode());
        }
        return code;
    }

    private static void checkDates(CompetencyDefinition cd) {
        if (cd.getEffectiveTo() != null && cd.getEffectiveTo().isBefore(cd.getEffectiveFrom())) {
            throw ApiException.badRequest("Ngày hết hiệu lực phải sau ngày bắt đầu");
        }
    }

    private static String label(Standard s, Code c, AssessmentRole r) {
        return s.getCode() + " / " + (c == null ? "toàn tiêu chuẩn" : "code " + c.getValue()) + " / " + r.getCode();
    }

    @Transactional
    public void activate(UUID id) {
        CompetencyDefinition cd = findEntity(id);
        String oldStatus = cd.getStatus();
        cd.setStatus("ACTIVE");
        repository.save(cd);
        audit.record("ACTIVATE", "COMPETENCY_DEFINITION", id, oldStatus, "ACTIVE", "Kích hoạt định nghĩa năng lực");
    }

    @Transactional
    public void retire(UUID id) {
        CompetencyDefinition cd = findEntity(id);
        String oldStatus = cd.getStatus();
        cd.setStatus("RETIRED");
        repository.save(cd);
        audit.record("RETIRE", "COMPETENCY_DEFINITION", id, oldStatus, "RETIRED", "Ngừng hiệu lực định nghĩa năng lực");
    }

    CompetencyDefinition findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("CompetencyDefinition", id));
    }

    public CompetencyDefinitionDto toDto(CompetencyDefinition cd) {
        Code c = cd.getCode();
        return new CompetencyDefinitionDto(
                cd.getId(),
                cd.getScheme().getId(),
                cd.getScheme().getCode(),
                cd.getScheme().getName(),
                cd.getStandard().getId(),
                cd.getStandard().getCode(),
                cd.getStandard().getName(),
                c == null ? null : c.getId(),
                c == null ? null : c.getValue(),
                c == null ? null : c.getName(),
                cd.getAssessmentRole().getId(),
                cd.getAssessmentRole().getCode(),
                cd.getAssessmentRole().getName(),
                cd.getDefaultValidityMonths(),
                cd.getEffectiveFrom(),
                cd.getEffectiveTo(),
                cd.getVersion(),
                cd.getStatus(),
                cd.getCriteria());
    }
}
