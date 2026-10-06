package com.npcore.ems.masterdata;

import com.npcore.ems.identity.DepartmentService;
import com.npcore.ems.masterdata.SimpleMasterServices.ActivityDto;
import com.npcore.ems.masterdata.SimpleMasterServices.ActivityRequest;
import com.npcore.ems.masterdata.SimpleMasterServices.ActivityService;
import com.npcore.ems.masterdata.SimpleMasterServices.AssessmentRoleDto;
import com.npcore.ems.masterdata.SimpleMasterServices.AssessmentRoleRequest;
import com.npcore.ems.masterdata.SimpleMasterServices.AssessmentRoleService;
import com.npcore.ems.masterdata.SimpleMasterServices.EducationFieldDto;
import com.npcore.ems.masterdata.SimpleMasterServices.EducationFieldRequest;
import com.npcore.ems.masterdata.SimpleMasterServices.EducationFieldService;
import com.npcore.ems.masterdata.SimpleMasterServices.IndustryDto;
import com.npcore.ems.masterdata.SimpleMasterServices.IndustryRequest;
import com.npcore.ems.masterdata.SimpleMasterServices.IndustryService;
import com.npcore.ems.masterdata.SimpleMasterServices.LocationDto;
import com.npcore.ems.masterdata.SimpleMasterServices.LocationRequest;
import com.npcore.ems.masterdata.SimpleMasterServices.LocationService;
import com.npcore.ems.masterdata.SimpleMasterServices.SchemeDto;
import com.npcore.ems.masterdata.SimpleMasterServices.SchemeRequest;
import com.npcore.ems.masterdata.SimpleMasterServices.SchemeService;
import com.npcore.ems.shared.web.ImportResult;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Danh mục (Module 1.4, 1.5, 3). Đọc: mọi user đăng nhập. Ghi: MASTER_DATA_MANAGE. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MasterDataController {

    private static final String WRITE = "hasAuthority('MASTER_DATA_MANAGE')";

    private final DepartmentService departments;
    private final AssessmentRoleService assessmentRoles;
    private final SchemeService schemes;
    private final StandardService standards;
    private final CodeSetService codeSets;
    private final IndustryService industries;
    private final ActivityService activities;
    private final LocationService locations;
    private final EducationFieldService educationFields;
    private final DegreeLevelRepository degreeLevels;

    // ---- Departments
    @GetMapping("/departments")
    public List<DepartmentService.DepartmentDto> departments() { return departments.list(); }

    @PostMapping("/departments") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public DepartmentService.DepartmentDto createDepartment(@RequestBody @Valid DepartmentService.DepartmentRequest r) { return departments.create(r); }

    @PutMapping("/departments/{id}") @PreAuthorize(WRITE)
    public DepartmentService.DepartmentDto updateDepartment(@PathVariable UUID id, @RequestBody @Valid DepartmentService.DepartmentRequest r) { return departments.update(id, r); }

    // ---- Assessment roles
    @GetMapping("/assessment-roles")
    public List<AssessmentRoleDto> assessmentRoles() { return assessmentRoles.list(); }

    @PostMapping("/assessment-roles") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public AssessmentRoleDto createAssessmentRole(@RequestBody @Valid AssessmentRoleRequest r) { return assessmentRoles.create(r); }

    @PutMapping("/assessment-roles/{id}") @PreAuthorize(WRITE)
    public AssessmentRoleDto updateAssessmentRole(@PathVariable UUID id, @RequestBody @Valid AssessmentRoleRequest r) { return assessmentRoles.update(id, r); }

    // ---- Schemes
    @GetMapping("/schemes")
    public List<SchemeDto> schemes() { return schemes.list(); }

    @PostMapping("/schemes") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public SchemeDto createScheme(@RequestBody @Valid SchemeRequest r) { return schemes.create(r); }

    @PutMapping("/schemes/{id}") @PreAuthorize(WRITE)
    public SchemeDto updateScheme(@PathVariable UUID id, @RequestBody @Valid SchemeRequest r) { return schemes.update(id, r); }

    // ---- Standards & versions
    @GetMapping("/standards")
    public List<StandardService.StandardDto> standards(@RequestParam(required = false) UUID schemeId) { return standards.list(schemeId); }

    @PostMapping("/standards") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public StandardService.StandardDto createStandard(@RequestBody @Valid StandardService.StandardRequest r) { return standards.create(r); }

    @PutMapping("/standards/{id}") @PreAuthorize(WRITE)
    public StandardService.StandardDto updateStandard(@PathVariable UUID id, @RequestBody @Valid StandardService.StandardRequest r) { return standards.update(id, r); }

    @GetMapping("/standards/{id}/versions")
    public List<StandardService.VersionDto> standardVersions(@PathVariable UUID id) { return standards.versions(id); }

    @PostMapping("/standards/{id}/versions") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public StandardService.VersionDto addStandardVersion(@PathVariable UUID id, @RequestBody @Valid StandardService.VersionRequest r) { return standards.addVersion(id, r); }

    @PutMapping("/standard-versions/{id}") @PreAuthorize(WRITE)
    public StandardService.VersionDto updateStandardVersion(@PathVariable UUID id, @RequestBody @Valid StandardService.VersionRequest r) { return standards.updateVersion(id, r); }

    // ---- Code sets & codes
    @GetMapping("/code-sets")
    public List<CodeSetService.CodeSetDto> codeSets(@RequestParam(required = false) UUID schemeId) { return codeSets.list(schemeId); }

    @PostMapping("/code-sets") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public CodeSetService.CodeSetDto createCodeSet(@RequestBody @Valid CodeSetService.CodeSetRequest r) { return codeSets.create(r); }

    @PostMapping("/code-sets/{id}/activate") @PreAuthorize(WRITE)
    public CodeSetService.CodeSetDto activateCodeSet(@PathVariable UUID id) { return codeSets.activate(id); }

    @GetMapping("/codes")
    public List<CodeSetService.CodeDto> allCodes() { return codeSets.listAllActiveCodes(); }

    @GetMapping("/code-sets/{id}/codes")
    public List<CodeSetService.CodeDto> codes(@PathVariable UUID id) { return codeSets.listCodes(id); }

    @PostMapping("/code-sets/{id}/codes") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public CodeSetService.CodeDto addCode(@PathVariable UUID id, @RequestBody @Valid CodeSetService.CodeRequest r) { return codeSets.addCode(id, r); }

    @PutMapping("/codes/{id}") @PreAuthorize(WRITE)
    public CodeSetService.CodeDto updateCode(@PathVariable UUID id, @RequestBody @Valid CodeSetService.CodeUpdateRequest r) { return codeSets.updateCode(id, r); }

    @PostMapping(path = "/code-sets/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize(WRITE)
    public ImportResult importCodes(@PathVariable UUID id, @RequestPart("file") MultipartFile file) { return codeSets.importCodes(id, file); }

    // ---- Industry / Activity / Location / Education field
    @GetMapping("/industries")
    public List<IndustryDto> industries() { return industries.list(); }

    @PostMapping("/industries") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public IndustryDto createIndustry(@RequestBody @Valid IndustryRequest r) { return industries.create(r); }

    @PutMapping("/industries/{id}") @PreAuthorize(WRITE)
    public IndustryDto updateIndustry(@PathVariable UUID id, @RequestBody @Valid IndustryRequest r) { return industries.update(id, r); }

    @GetMapping("/activities")
    public List<ActivityDto> activities() { return activities.list(); }

    @PostMapping("/activities") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public ActivityDto createActivity(@RequestBody @Valid ActivityRequest r) { return activities.create(r); }

    @PutMapping("/activities/{id}") @PreAuthorize(WRITE)
    public ActivityDto updateActivity(@PathVariable UUID id, @RequestBody @Valid ActivityRequest r) { return activities.update(id, r); }

    @GetMapping("/locations")
    public List<LocationDto> locations() { return locations.list(); }

    @PostMapping("/locations") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public LocationDto createLocation(@RequestBody @Valid LocationRequest r) { return locations.create(r); }

    @PutMapping("/locations/{id}") @PreAuthorize(WRITE)
    public LocationDto updateLocation(@PathVariable UUID id, @RequestBody @Valid LocationRequest r) { return locations.update(id, r); }

    @GetMapping("/education-fields")
    public List<EducationFieldDto> educationFields() { return educationFields.list(); }

    @PostMapping("/education-fields") @PreAuthorize(WRITE) @ResponseStatus(HttpStatus.CREATED)
    public EducationFieldDto createEducationField(@RequestBody @Valid EducationFieldRequest r) { return educationFields.create(r); }

    @PutMapping("/education-fields/{id}") @PreAuthorize(WRITE)
    public EducationFieldDto updateEducationField(@PathVariable UUID id, @RequestBody @Valid EducationFieldRequest r) { return educationFields.update(id, r); }

    public record DegreeLevelDto(String code, String name, int rankOrder) {}

    @GetMapping("/degree-levels")
    public List<DegreeLevelDto> degreeLevels() {
        return degreeLevels.findAll().stream().sorted(Comparator.comparingInt(DegreeLevel::getRankOrder))
                .map(d -> new DegreeLevelDto(d.getCode(), d.getName(), d.getRankOrder())).toList();
    }
}
