package com.npcore.ems.competency;

import com.npcore.ems.competency.CompetencyDtos.CompetencyActionRequest;
import com.npcore.ems.competency.CompetencyDtos.CompetencyDefinitionDto;
import com.npcore.ems.competency.CompetencyDtos.CompetencyDefinitionRequest;
import com.npcore.ems.competency.CompetencyDtos.EvidenceDto;
import com.npcore.ems.competency.CompetencyDtos.EvidenceRequest;
import com.npcore.ems.competency.CompetencyDtos.ExpertCompetencyDto;
import com.npcore.ems.competency.CompetencyDtos.ExpertCompetencyRequest;
import com.npcore.ems.competency.CompetencyDtos.MatrixRow;
import com.npcore.ems.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CompetencyController {

    private final CompetencyDefinitionService definitionService;
    private final ExpertCompetencyService expertCompetencyService;

    // ==================== Competency Definitions ====================

    @GetMapping("/competency-definitions")
    public PageResponse<CompetencyDefinitionDto> listDefinitions(
            @RequestParam(required = false) UUID schemeId,
            @RequestParam(required = false) UUID standardId,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return definitionService.list(schemeId, standardId, roleId, status, q, pageable);
    }

    @GetMapping("/competency-definitions/active")
    public List<CompetencyDefinitionDto> listActiveDefinitions(
            @RequestParam(required = false) UUID standardId) {
        return definitionService.listAllActive(standardId);
    }

    @GetMapping("/competency-definitions/{id}")
    public CompetencyDefinitionDto getDefinition(@PathVariable UUID id) {
        return definitionService.getById(id);
    }

    @PostMapping("/competency-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public CompetencyDefinitionDto createDefinition(@RequestBody @Valid CompetencyDefinitionRequest req) {
        return definitionService.create(req);
    }

    @PostMapping("/competency-definitions/bulk")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public CompetencyDtos.BulkResult createDefinitionsBulk(@RequestBody @Valid CompetencyDtos.BulkDefinitionRequest req) {
        return definitionService.createBulk(req);
    }

    @PutMapping("/competency-definitions/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public CompetencyDefinitionDto updateDefinition(
            @PathVariable UUID id,
            @RequestBody @Valid CompetencyDefinitionRequest req) {
        return definitionService.update(id, req);
    }

    @PostMapping("/competency-definitions/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public void activateDefinition(@PathVariable UUID id) {
        definitionService.activate(id);
    }

    @PostMapping("/competency-definitions/{id}/retire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public void retireDefinition(@PathVariable UUID id) {
        definitionService.retire(id);
    }

    // ==================== Expert Competencies ====================

    @GetMapping("/experts/{expertId}/competencies")
    @PreAuthorize("hasAuthority('EXPERT_VIEW')")
    public PageResponse<ExpertCompetencyDto> getCompetenciesForExpert(
            @PathVariable UUID expertId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return expertCompetencyService.getForExpert(expertId, status, pageable);
    }

    @PostMapping("/experts/{expertId}/competencies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EXPERT_COMPETENCY_EDIT')")
    public ExpertCompetencyDto addCompetency(
            @PathVariable UUID expertId,
            @RequestBody @Valid ExpertCompetencyRequest req) {
        return expertCompetencyService.add(expertId, req);
    }

    @GetMapping("/experts/{expertId}/competencies/{id}")
    @PreAuthorize("hasAuthority('EXPERT_VIEW')")
    public ExpertCompetencyDto getCompetency(
            @PathVariable UUID expertId,
            @PathVariable UUID id) {
        return expertCompetencyService.getById(id);
    }

    @PostMapping("/experts/{expertId}/competencies/{id}/actions")
    public ExpertCompetencyDto transitionCompetency(
            @PathVariable UUID expertId,
            @PathVariable UUID id,
            @RequestBody @Valid CompetencyActionRequest req) {
        return expertCompetencyService.transition(id, req);
    }

    @PostMapping("/experts/{expertId}/competencies/{id}/evidences")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EXPERT_COMPETENCY_EDIT')")
    public EvidenceDto addEvidence(
            @PathVariable UUID expertId,
            @PathVariable UUID id,
            @RequestBody @Valid EvidenceRequest req) {
        return expertCompetencyService.addEvidence(id, req);
    }

    @DeleteMapping("/experts/{expertId}/competencies/{id}/evidences/{evidenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EXPERT_COMPETENCY_EDIT')")
    public void removeEvidence(
            @PathVariable UUID expertId,
            @PathVariable UUID id,
            @PathVariable UUID evidenceId) {
        expertCompetencyService.removeEvidence(id, evidenceId);
    }

    // ==================== Competency Matrix ====================

    @GetMapping("/competency-matrix")
    @PreAuthorize("hasAuthority('EXPERT_VIEW')")
    public CompetencyDtos.MatrixResponse getMatrix(@RequestParam UUID standardId,
                                                   @RequestParam(required = false) UUID roleId,
                                                   @RequestParam(defaultValue = "false") boolean includeExpired) {
        return expertCompetencyService.getMatrix(standardId, roleId, includeExpired);
    }
}
