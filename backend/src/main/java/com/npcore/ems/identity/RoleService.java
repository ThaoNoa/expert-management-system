package com.npcore.ems.identity;

import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-1.2/1.3: role tuỳ chỉnh, phân quyền theo bảng role_permissions (không hard-code tên role). */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> SCOPES = Set.of("ALL", "DEPARTMENT", "OWN");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRepository users;
    private final AuditService audit;

    public record Grant(@NotBlank String code, @NotNull String dataScope) {}

    public record RoleRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z0-9_]+", message = "chỉ gồm A-Z, 0-9, _") String roleCode,
            @NotBlank @Size(max = 255) String roleName,
            String description,
            @NotNull List<@Valid Grant> permissions) {}

    public record RoleDto(UUID id, String roleCode, String roleName, String description, boolean system,
                          List<Grant> permissions) {
        static RoleDto from(Role r) {
            return new RoleDto(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isSystem(),
                    r.getPermissions().stream()
                            .map(p -> new Grant(p.getPermission().getCode(), p.getDataScope()))
                            .sorted(Comparator.comparing(Grant::code)).toList());
        }
    }

    public record PermissionDto(String code, String name, String module) {}

    @Transactional(readOnly = true)
    public List<RoleDto> list() {
        return roles.findAll().stream().sorted(Comparator.comparing(Role::getCode)).map(RoleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> permissions() {
        return permissions.findAll().stream()
                .sorted(Comparator.comparing(Permission::getModule).thenComparing(Permission::getCode))
                .map(p -> new PermissionDto(p.getCode(), p.getName(), p.getModule())).toList();
    }

    @Transactional
    public RoleDto create(RoleRequest req) {
        if (roles.existsByCode(req.roleCode())) throw ApiException.duplicate("Mã role đã tồn tại");
        Role r = new Role();
        r.setCode(req.roleCode());
        apply(r, req);
        roles.saveAndFlush(r);
        setGrants(r, req.permissions());
        RoleDto dto = RoleDto.from(r);
        audit.record("CREATE", "ROLE", r.getId(), null, dto, null);
        return dto;
    }

    @Transactional
    public RoleDto update(UUID id, RoleRequest req) {
        Role r = roles.findById(id).orElseThrow(() -> ApiException.notFound("Role", id));
        if (!r.getCode().equals(req.roleCode())) throw ApiException.badRequest("Không đổi được mã role");
        RoleDto before = RoleDto.from(r);
        apply(r, req);
        setGrants(r, req.permissions());
        RoleDto after = RoleDto.from(r);
        audit.record("UPDATE", "ROLE", id, before, after, null);
        return after;
    }

    /** BR-1.2.2: không xoá role đang được gán; role hệ thống không xoá. */
    @Transactional
    public void delete(UUID id) {
        Role r = roles.findById(id).orElseThrow(() -> ApiException.notFound("Role", id));
        if (r.isSystem()) throw ApiException.conflict("Không xoá được role hệ thống");
        if (users.existsByRoleId(id)) throw ApiException.conflict("Role đang được gán cho người dùng");
        audit.record("DELETE", "ROLE", id, RoleDto.from(r), null, null);
        roles.delete(r);
    }

    private static void apply(Role r, RoleRequest req) {
        r.setName(req.roleName());
        r.setDescription(req.description());
    }

    private void setGrants(Role r, List<Grant> grants) {
        Map<String, Permission> byCode = permissions.findByCodeIn(grants.stream().map(Grant::code).toList())
                .stream().collect(Collectors.toMap(Permission::getCode, Function.identity()));
        r.getPermissions().clear();
        roles.flush();
        for (Grant g : grants) {
            Permission p = byCode.get(g.code());
            if (p == null) throw ApiException.badRequest("Quyền không tồn tại: " + g.code());
            if (!SCOPES.contains(g.dataScope())) throw ApiException.badRequest("dataScope không hợp lệ: " + g.dataScope());
            r.getPermissions().add(new RolePermission(r, p, g.dataScope()));
        }
    }
}
