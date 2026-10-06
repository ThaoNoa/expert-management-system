package com.npcore.ems.masterdata;

import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.web.ApiException;
import com.npcore.ems.shared.web.SimpleCrudService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Các danh mục đơn giản dùng chung khung {@link SimpleCrudService}. */
public final class SimpleMasterServices {
    private SimpleMasterServices() {}

    static void ensureUnique(Optional<?> existing, Function<Object, UUID> idOf, UUID excludeId, String message) {
        existing.ifPresent(e -> {
            if (!idOf.apply(e).equals(excludeId)) throw ApiException.duplicate(message);
        });
    }

    // ---------------- Assessment role ----------------
    public record AssessmentRoleDto(UUID id, String roleCode, String roleName, String description,
                                    boolean countsForCoverage, int sortOrder, String status) {}

    public record AssessmentRoleRequest(@NotBlank @Size(max = 20) String roleCode, @NotBlank String roleName,
                                        String description, boolean countsForCoverage, int sortOrder,
                                        @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    @Service
    public static class AssessmentRoleService extends SimpleCrudService<AssessmentRole, AssessmentRoleDto, AssessmentRoleRequest> {
        private final AssessmentRoleRepository repo;

        public AssessmentRoleService(AssessmentRoleRepository repo, AuditService audit) {
            super(repo, audit, "ASSESSMENT_ROLE", "Vai trò đánh giá");
            this.repo = repo;
        }

        @Override protected AssessmentRoleDto toDto(AssessmentRole e) {
            return new AssessmentRoleDto(e.getId(), e.getCode(), e.getName(), e.getDescription(),
                    e.isCountsForCoverage(), e.getSortOrder(), e.getStatus());
        }
        @Override protected AssessmentRole newEntity() { return new AssessmentRole(); }
        @Override protected UUID idOf(AssessmentRole e) { return e.getId(); }
        @Override protected Sort defaultSort() { return Sort.by("sortOrder", "code"); }
        @Override protected void apply(AssessmentRole e, AssessmentRoleRequest r) {
            e.setCode(r.roleCode().trim().toUpperCase());
            e.setName(r.roleName().trim());
            e.setDescription(r.description());
            e.setCountsForCoverage(r.countsForCoverage());
            e.setSortOrder(r.sortOrder());
            if (r.status() != null) e.setStatus(r.status());
        }
        @Override protected void validate(AssessmentRoleRequest r, UUID excludeId) {
            ensureUnique(repo.findByCodeIgnoreCase(r.roleCode().trim()), o -> ((AssessmentRole) o).getId(), excludeId,
                    "Mã vai trò đã tồn tại");
        }
    }

    // ---------------- Scheme ----------------
    public record SchemeDto(UUID id, String schemeCode, String schemeName, String description,
                            boolean parentCoversChild, String status) {}

    public record SchemeRequest(@NotBlank @Size(max = 50) String schemeCode, @NotBlank String schemeName,
                                String description, boolean parentCoversChild,
                                @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    @Service
    public static class SchemeService extends SimpleCrudService<Scheme, SchemeDto, SchemeRequest> {
        private final SchemeRepository repo;

        public SchemeService(SchemeRepository repo, AuditService audit) {
            super(repo, audit, "SCHEME", "Scheme");
            this.repo = repo;
        }

        @Override protected SchemeDto toDto(Scheme e) {
            return new SchemeDto(e.getId(), e.getCode(), e.getName(), e.getDescription(), e.isParentCoversChild(), e.getStatus());
        }
        @Override protected Scheme newEntity() { return new Scheme(); }
        @Override protected UUID idOf(Scheme e) { return e.getId(); }
        @Override protected Sort defaultSort() { return Sort.by("code"); }
        @Override protected void apply(Scheme e, SchemeRequest r) {
            e.setCode(r.schemeCode().trim().toUpperCase());
            e.setName(r.schemeName().trim());
            e.setDescription(r.description());
            e.setParentCoversChild(r.parentCoversChild());
            if (r.status() != null) e.setStatus(r.status());
        }
        @Override protected void validate(SchemeRequest r, UUID excludeId) {
            ensureUnique(repo.findByCodeIgnoreCase(r.schemeCode().trim()), o -> ((Scheme) o).getId(), excludeId,
                    "Mã scheme đã tồn tại");
        }
    }

    // ---------------- Industry / Activity ----------------
    public record IndustryDto(UUID id, String industryCode, String industryName, String description) {}

    public record IndustryRequest(@NotBlank @Size(max = 50) String industryCode, @NotBlank String industryName,
                                  String description) {}

    @Service
    public static class IndustryService extends SimpleCrudService<Industry, IndustryDto, IndustryRequest> {
        private final IndustryRepository repo;

        public IndustryService(IndustryRepository repo, AuditService audit) {
            super(repo, audit, "INDUSTRY", "Ngành");
            this.repo = repo;
        }

        @Override protected IndustryDto toDto(Industry e) {
            return new IndustryDto(e.getId(), e.getCode(), e.getName(), e.getDescription());
        }
        @Override protected Industry newEntity() { return new Industry(); }
        @Override protected UUID idOf(Industry e) { return e.getId(); }
        @Override protected Sort defaultSort() { return Sort.by("code"); }
        @Override protected void apply(Industry e, IndustryRequest r) {
            e.setCode(r.industryCode().trim());
            e.setName(r.industryName().trim());
            e.setDescription(r.description());
        }
        @Override protected void validate(IndustryRequest r, UUID excludeId) {
            ensureUnique(repo.findByCodeIgnoreCase(r.industryCode().trim()), o -> ((Industry) o).getId(), excludeId,
                    "Mã ngành đã tồn tại");
        }
    }

    public record ActivityDto(UUID id, String activityCode, String activityName, String description) {}

    public record ActivityRequest(@NotBlank @Size(max = 50) String activityCode, @NotBlank String activityName,
                                  String description) {}

    @Service
    public static class ActivityService extends SimpleCrudService<Activity, ActivityDto, ActivityRequest> {
        private final ActivityRepository repo;

        public ActivityService(ActivityRepository repo, AuditService audit) {
            super(repo, audit, "ACTIVITY", "Hoạt động");
            this.repo = repo;
        }

        @Override protected ActivityDto toDto(Activity e) {
            return new ActivityDto(e.getId(), e.getCode(), e.getName(), e.getDescription());
        }
        @Override protected Activity newEntity() { return new Activity(); }
        @Override protected UUID idOf(Activity e) { return e.getId(); }
        @Override protected Sort defaultSort() { return Sort.by("code"); }
        @Override protected void apply(Activity e, ActivityRequest r) {
            e.setCode(r.activityCode().trim());
            e.setName(r.activityName().trim());
            e.setDescription(r.description());
        }
        @Override protected void validate(ActivityRequest r, UUID excludeId) {
            ensureUnique(repo.findByCodeIgnoreCase(r.activityCode().trim()), o -> ((Activity) o).getId(), excludeId,
                    "Mã hoạt động đã tồn tại");
        }
    }

    // ---------------- Location ----------------
    public record LocationDto(UUID id, String locationName, String province, String country, String region,
                              BigDecimal latitude, BigDecimal longitude) {}

    public record LocationRequest(@NotBlank String locationName, String province,
                                  @Pattern(regexp = "[A-Z]{2}") String country, String region,
                                  @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                  @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}

    @Service
    public static class LocationService extends SimpleCrudService<Location, LocationDto, LocationRequest> {
        public LocationService(LocationRepository repo, AuditService audit) {
            super(repo, audit, "LOCATION", "Địa điểm");
        }

        @Override protected LocationDto toDto(Location e) {
            return new LocationDto(e.getId(), e.getName(), e.getProvince(), e.getCountry(), e.getRegion(),
                    e.getLatitude(), e.getLongitude());
        }
        @Override protected Location newEntity() { return new Location(); }
        @Override protected UUID idOf(Location e) { return e.getId(); }
        @Override protected Sort defaultSort() { return Sort.by("province", "name"); }
        @Override protected void apply(Location e, LocationRequest r) {
            e.setName(r.locationName().trim());
            e.setProvince(r.province());
            e.setCountry(r.country() == null ? "VN" : r.country());
            e.setRegion(r.region());
            e.setLatitude(r.latitude());
            e.setLongitude(r.longitude());
        }
    }

    // ---------------- Education field ----------------
    public record EducationFieldDto(UUID id, String fieldCode, String fieldName, UUID parentId) {}

    public record EducationFieldRequest(@NotBlank @Size(max = 50) String fieldCode, @NotBlank String fieldName,
                                        UUID parentId) {}

    @Service
    public static class EducationFieldService extends SimpleCrudService<EducationField, EducationFieldDto, EducationFieldRequest> {
        private final EducationFieldRepository repo;

        public EducationFieldService(EducationFieldRepository repo, AuditService audit) {
            super(repo, audit, "EDUCATION_FIELD", "Lĩnh vực đào tạo");
            this.repo = repo;
        }

        @Override protected EducationFieldDto toDto(EducationField e) {
            return new EducationFieldDto(e.getId(), e.getCode(), e.getName(), e.getParentId());
        }
        @Override protected EducationField newEntity() { return new EducationField(); }
        @Override protected UUID idOf(EducationField e) { return e.getId(); }
        @Override protected Sort defaultSort() { return Sort.by("code"); }
        @Override protected void apply(EducationField e, EducationFieldRequest r) {
            if (r.parentId() != null && r.parentId().equals(e.getId())) throw ApiException.badRequest("Lĩnh vực cha không hợp lệ");
            e.setCode(r.fieldCode().trim());
            e.setName(r.fieldName().trim());
            e.setParentId(r.parentId());
        }
        @Override protected void validate(EducationFieldRequest r, UUID excludeId) {
            ensureUnique(repo.findByCodeIgnoreCase(r.fieldCode().trim()), o -> ((EducationField) o).getId(), excludeId,
                    "Mã lĩnh vực đã tồn tại");
        }
    }
}
