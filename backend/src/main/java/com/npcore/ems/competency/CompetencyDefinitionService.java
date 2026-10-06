package com.npcore.ems.competency;

import com.npcore.ems.competency.CompetencyDtos.CompetencyDefinitionDto;
import com.npcore.ems.competency.CompetencyDtos.CompetencyDefinitionRequest;
import com.npcore.ems.masterdata.AssessmentRole;
import com.npcore.ems.masterdata.AssessmentRoleRepository;
import com.npcore.ems.masterdata.Code;
import com.npcore.ems.masterdata.CodeRepository;
import com.npcore.ems.masterdata.Scheme;
import com.npcore.ems.masterdata.SchemeRepository;
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
    private final SchemeRepository schemes;
    private final StandardRepository standards;
    private final CodeRepository codes;
    private final AssessmentRoleRepository assessmentRoles;
    private final AuditService audit;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<CompetencyDefinitionDto> list(UUID schemeId, UUID standardId, String status, Pageable pageable) {
        Specification<CompetencyDefinition> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (schemeId != null) ps.add(cb.equal(root.get("scheme").get("id"), schemeId));
            if (standardId != null) ps.add(cb.equal(root.get("standard").get("id"), standardId));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<CompetencyDefinition> page = repository.findAll(spec, pageable);
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
        Scheme scheme = schemes.findById(req.schemeId())
                .orElseThrow(() -> ApiException.notFound("Scheme", req.schemeId()));
        Standard standard = standards.findById(req.standardId())
                .orElseThrow(() -> ApiException.notFound("Standard", req.standardId()));
        AssessmentRole role = assessmentRoles.findById(req.assessmentRoleId())
                .orElseThrow(() -> ApiException.notFound("AssessmentRole", req.assessmentRoleId()));
        Code code = null;
        if (req.codeId() != null) {
            code = codes.findById(req.codeId())
                    .orElseThrow(() -> ApiException.notFound("Code", req.codeId()));
        }

        String version = req.version() != null && !req.version().isBlank() ? req.version() : "1";
        if (repository.existsByStandardIdAndCodeIdAndAssessmentRoleIdAndVersion(
                standard.getId(), code == null ? null : code.getId(), role.getId(), version)) {
            throw ApiException.duplicate("Định nghĩa năng lực này đã tồn tại với phiên bản: " + version);
        }

        CompetencyDefinition cd = new CompetencyDefinition();
        cd.setScheme(scheme);
        cd.setStandard(standard);
        cd.setCode(code);
        cd.setAssessmentRole(role);
        cd.setDefaultValidityMonths(req.defaultValidityMonths());
        cd.setVersion(version);
        cd.setEffectiveFrom(req.effectiveFrom());
        cd.setEffectiveTo(req.effectiveTo());
        cd.setStatus(req.status() != null ? req.status() : "ACTIVE");
        cd.setCriteria(req.criteria() != null ? req.criteria() : objectMapper.createObjectNode());

        CompetencyDefinition saved = repository.save(cd);
        audit.record("CREATE", "COMPETENCY_DEFINITION", saved.getId(), null, toDto(saved), "Tạo định nghĩa năng lực");
        return toDto(saved);
    }

    @Transactional
    public CompetencyDefinitionDto update(UUID id, CompetencyDefinitionRequest req) {
        CompetencyDefinition cd = findEntity(id);
        if ("RETIRED".equals(cd.getStatus())) {
            throw ApiException.badRequest("Không thể chỉnh sửa định nghĩa năng lực đã ngừng hiệu lực (RETIRED)");
        }
        CompetencyDefinitionDto oldDto = toDto(cd);

        if (req.schemeId() != null && !req.schemeId().equals(cd.getScheme().getId())) {
            cd.setScheme(schemes.findById(req.schemeId())
                    .orElseThrow(() -> ApiException.notFound("Scheme", req.schemeId())));
        }
        if (req.standardId() != null && !req.standardId().equals(cd.getStandard().getId())) {
            cd.setStandard(standards.findById(req.standardId())
                    .orElseThrow(() -> ApiException.notFound("Standard", req.standardId())));
        }
        if (req.assessmentRoleId() != null && !req.assessmentRoleId().equals(cd.getAssessmentRole().getId())) {
            cd.setAssessmentRole(assessmentRoles.findById(req.assessmentRoleId())
                    .orElseThrow(() -> ApiException.notFound("AssessmentRole", req.assessmentRoleId())));
        }
        if (req.codeId() != null) {
            cd.setCode(codes.findById(req.codeId())
                    .orElseThrow(() -> ApiException.notFound("Code", req.codeId())));
        } else {
            cd.setCode(null);
        }

        if (req.defaultValidityMonths() != null) cd.setDefaultValidityMonths(req.defaultValidityMonths());
        if (req.effectiveFrom() != null) cd.setEffectiveFrom(req.effectiveFrom());
        cd.setEffectiveTo(req.effectiveTo());
        if (req.criteria() != null) cd.setCriteria(req.criteria());

        CompetencyDefinition updated = repository.save(cd);
        audit.record("UPDATE", "COMPETENCY_DEFINITION", id, oldDto, toDto(updated), "Cập nhật định nghĩa năng lực");
        return toDto(updated);
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
