package com.npcore.ems.identity;

import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.web.ApiException;
import com.npcore.ems.shared.web.SimpleCrudService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class DepartmentService extends SimpleCrudService<Department, DepartmentService.DepartmentDto, DepartmentService.DepartmentRequest> {

    private final DepartmentRepository departments;

    public record DepartmentDto(UUID id, String departmentCode, String departmentName, UUID parentId, String status) {}

    public record DepartmentRequest(@NotBlank @Size(max = 50) String departmentCode,
                                    @NotBlank @Size(max = 255) String departmentName,
                                    UUID parentId,
                                    @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    public DepartmentService(DepartmentRepository repo, AuditService audit) {
        super(repo, audit, "DEPARTMENT", "Phòng ban");
        this.departments = repo;
    }

    @Override
    protected DepartmentDto toDto(Department d) {
        return new DepartmentDto(d.getId(), d.getCode(), d.getName(), d.getParentId(), d.getStatus());
    }

    @Override
    protected Department newEntity() {
        return new Department();
    }

    @Override
    protected void apply(Department d, DepartmentRequest r) {
        if (r.parentId() != null && r.parentId().equals(d.getId())) throw ApiException.badRequest("Phòng ban cha không hợp lệ");
        d.setCode(r.departmentCode().trim());
        d.setName(r.departmentName().trim());
        d.setParentId(r.parentId());
        if (r.status() != null) d.setStatus(r.status());
    }

    @Override
    protected UUID idOf(Department d) {
        return d.getId();
    }

    @Override
    protected void validate(DepartmentRequest r, UUID excludeId) {
        boolean dup = departments.findAll().stream()
                .anyMatch(d -> d.getCode().equalsIgnoreCase(r.departmentCode().trim()) && !d.getId().equals(excludeId));
        if (dup) throw ApiException.duplicate("Mã phòng ban đã tồn tại");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by("code");
    }
}
