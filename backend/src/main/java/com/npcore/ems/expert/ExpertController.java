package com.npcore.ems.expert;

import com.npcore.ems.expert.ExpertDtos.CertificateDto;
import com.npcore.ems.expert.ExpertDtos.CertificateRequest;
import com.npcore.ems.expert.ExpertDtos.EducationDto;
import com.npcore.ems.expert.ExpertDtos.EducationRequest;
import com.npcore.ems.expert.ExpertDtos.ExperienceDto;
import com.npcore.ems.expert.ExpertDtos.ExperienceRequest;
import com.npcore.ems.expert.ExpertDtos.ExpertDetail;
import com.npcore.ems.expert.ExpertDtos.ExpertRequest;
import com.npcore.ems.expert.ExpertDtos.ExpertSummary;
import com.npcore.ems.expert.ExpertDtos.HistoryEntry;
import com.npcore.ems.expert.ExpertDtos.LanguageDto;
import com.npcore.ems.expert.ExpertDtos.LanguageRequest;
import com.npcore.ems.expert.ExpertDtos.StatusRequest;
import com.npcore.ems.expert.ExpertDtos.TrainingDto;
import com.npcore.ems.expert.ExpertDtos.TrainingRequest;
import com.npcore.ems.expert.ExpertProfileServices.CertificateService;
import com.npcore.ems.expert.ExpertProfileServices.EducationService;
import com.npcore.ems.expert.ExpertProfileServices.ExperienceService;
import com.npcore.ems.expert.ExpertProfileServices.TrainingService;
import com.npcore.ems.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Module 2 - Expert Profile. Phạm vi dữ liệu kiểm tra trong service (ExpertAccess). */
@RestController
@RequestMapping("/api/v1/experts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('EXPERT_VIEW')")
public class ExpertController {

    private final ExpertService experts;
    private final ExpertImportService importer;
    private final EducationService educations;
    private final ExperienceService experiences;
    private final TrainingService trainings;
    private final CertificateService certificates;
    private final LanguageService languages;

    @GetMapping
    public PageResponse<ExpertSummary> search(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String expertType,
                                              @RequestParam(required = false) String employmentType,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) UUID departmentId,
                                              @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return experts.search(q, expertType, employmentType, status, departmentId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EXPERT_CREATE')")
    public ExpertDetail create(@RequestBody @Valid ExpertRequest req) {
        return experts.create(req);
    }

    @GetMapping("/me")
    public ExpertDetail me() {
        return experts.me();
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expert-import-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(importer.templateCsv().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EXPERT_CREATE') and hasAuthority('EXPERT_COMPETENCY_EDIT')")
    public ExpertImportService.ExpertImportResult importFile(@RequestPart("file") MultipartFile file,
            @RequestParam(name = "createAccounts", defaultValue = "false") boolean createAccounts) {
        return importer.importFile(file, createAccounts);
    }

    @GetMapping("/{id}")
    public ExpertDetail get(@PathVariable UUID id) {
        return experts.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('EXPERT_EDIT', 'EXPERT_CONTACT_EDIT')")
    public ExpertDetail update(@PathVariable UUID id, @RequestBody @Valid ExpertRequest req) {
        return experts.update(id, req);
    }

    @PostMapping("/{id}/status")
    public ExpertDetail changeStatus(@PathVariable UUID id, @RequestBody @Valid StatusRequest req) {
        return experts.changeStatus(id, req.action(), req.comment(), req.suspendedUntil());
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntry> history(@PathVariable UUID id) {
        return experts.history(id);
    }

    // ---- Education
    @GetMapping("/{id}/educations")
    public List<EducationDto> educations(@PathVariable UUID id) { return educations.list(id); }

    @PostMapping("/{id}/educations") @ResponseStatus(HttpStatus.CREATED)
    public EducationDto addEducation(@PathVariable UUID id, @RequestBody @Valid EducationRequest r) { return educations.create(id, r); }

    @PutMapping("/{id}/educations/{itemId}")
    public EducationDto updateEducation(@PathVariable UUID id, @PathVariable UUID itemId, @RequestBody @Valid EducationRequest r) { return educations.update(id, itemId, r); }

    @DeleteMapping("/{id}/educations/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEducation(@PathVariable UUID id, @PathVariable UUID itemId) { educations.delete(id, itemId); }

    // ---- Experience
    @GetMapping("/{id}/experiences")
    public List<ExperienceDto> experiences(@PathVariable UUID id) { return experiences.list(id); }

    @PostMapping("/{id}/experiences") @ResponseStatus(HttpStatus.CREATED)
    public ExperienceDto addExperience(@PathVariable UUID id, @RequestBody @Valid ExperienceRequest r) { return experiences.create(id, r); }

    @PutMapping("/{id}/experiences/{itemId}")
    public ExperienceDto updateExperience(@PathVariable UUID id, @PathVariable UUID itemId, @RequestBody @Valid ExperienceRequest r) { return experiences.update(id, itemId, r); }

    @DeleteMapping("/{id}/experiences/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperience(@PathVariable UUID id, @PathVariable UUID itemId) { experiences.delete(id, itemId); }

    // ---- Training
    @GetMapping("/{id}/trainings")
    public List<TrainingDto> trainings(@PathVariable UUID id) { return trainings.list(id); }

    @PostMapping("/{id}/trainings") @ResponseStatus(HttpStatus.CREATED)
    public TrainingDto addTraining(@PathVariable UUID id, @RequestBody @Valid TrainingRequest r) { return trainings.create(id, r); }

    @PutMapping("/{id}/trainings/{itemId}")
    public TrainingDto updateTraining(@PathVariable UUID id, @PathVariable UUID itemId, @RequestBody @Valid TrainingRequest r) { return trainings.update(id, itemId, r); }

    @DeleteMapping("/{id}/trainings/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTraining(@PathVariable UUID id, @PathVariable UUID itemId) { trainings.delete(id, itemId); }

    // ---- Certificate
    @GetMapping("/{id}/certificates")
    public List<CertificateDto> certificates(@PathVariable UUID id) { return certificates.list(id); }

    @PostMapping("/{id}/certificates") @ResponseStatus(HttpStatus.CREATED)
    public CertificateDto addCertificate(@PathVariable UUID id, @RequestBody @Valid CertificateRequest r) { return certificates.create(id, r); }

    @PutMapping("/{id}/certificates/{itemId}")
    public CertificateDto updateCertificate(@PathVariable UUID id, @PathVariable UUID itemId, @RequestBody @Valid CertificateRequest r) { return certificates.update(id, itemId, r); }

    @DeleteMapping("/{id}/certificates/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCertificate(@PathVariable UUID id, @PathVariable UUID itemId) { certificates.delete(id, itemId); }

    // ---- Language
    @GetMapping("/{id}/languages")
    public List<LanguageDto> languages(@PathVariable UUID id) { return languages.list(id); }

    @PutMapping("/{id}/languages/{language}")
    public LanguageDto upsertLanguage(@PathVariable UUID id, @PathVariable String language, @RequestBody @Valid LanguageRequest r) { return languages.upsert(id, language, r); }

    @DeleteMapping("/{id}/languages/{language}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLanguage(@PathVariable UUID id, @PathVariable String language) { languages.delete(id, language); }
}
