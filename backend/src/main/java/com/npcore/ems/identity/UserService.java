package com.npcore.ems.identity;

import com.npcore.ems.identity.UserDtos.CreateUserRequest;
import com.npcore.ems.identity.UserDtos.UpdateUserRequest;
import com.npcore.ems.identity.UserDtos.UserDto;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.security.SecurityUtils;
import com.npcore.ems.shared.web.ApiException;
import com.npcore.ems.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(String q, String status, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isNull(root.get("deletedAt")));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResponse.of(users.findAll(spec, pageable), UserDto::from);
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return UserDto.from(load(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest req) {
        if (users.usernameTaken(req.username())) throw ApiException.duplicate("Tên đăng nhập đã tồn tại");
        if (users.emailTaken(req.email(), null)) throw ApiException.duplicate("Email đã được sử dụng"); // BR-1.1.1
        passwordPolicy.check(req.password());
        User u = new User();
        u.setUsername(req.username().trim());
        u.setEmail(req.email().trim());
        u.setFullName(req.fullName().trim());
        u.setPosition(req.position());
        u.setDepartment(req.departmentId() == null ? null : departments.findById(req.departmentId())
                .orElseThrow(() -> ApiException.notFound("Phòng ban", req.departmentId())));
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setPasswordChangedAt(OffsetDateTime.now());
        u.setRoles(new HashSet<>(resolveRoles(req.roleCodes())));
        u.setCreatedBy(SecurityUtils.currentUser().id());
        users.save(u);
        UserDto dto = UserDto.from(u);
        audit.record("CREATE", "USER", u.getId(), null, dto, null);
        return dto;
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest req) {
        User u = load(id);
        UserDto before = UserDto.from(u);
        if (users.emailTaken(req.email(), id)) throw ApiException.duplicate("Email đã được sử dụng");
        u.setEmail(req.email().trim());
        u.setFullName(req.fullName().trim());
        u.setPosition(req.position());
        u.setDepartment(req.departmentId() == null ? null : departments.findById(req.departmentId())
                .orElseThrow(() -> ApiException.notFound("Phòng ban", req.departmentId())));
        u.setRoles(new HashSet<>(resolveRoles(req.roleCodes())));
        u.setUpdatedBy(SecurityUtils.currentUser().id());
        UserDto after = UserDto.from(u);
        audit.record("UPDATE", "USER", id, before, after, null);
        return after;
    }

    @Transactional
    public void setEnabled(UUID id, boolean enabled) {
        User u = load(id);
        if (!enabled && u.getId().equals(SecurityUtils.currentUser().id())) {
            throw ApiException.businessRule("Không thể tự vô hiệu hoá tài khoản của mình");
        }
        String old = u.getStatus();
        u.setStatus(enabled ? "ACTIVE" : "DISABLED");
        if (enabled) u.setFailedLoginCount(0);       // mở khoá luôn tài khoản LOCKED
        else refreshTokens.revokeAllOfUser(id, OffsetDateTime.now());
        audit.record(enabled ? "ENABLE" : "DISABLE", "USER", id, old, u.getStatus(), null);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User u = load(id);
        passwordPolicy.check(newPassword);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setPasswordChangedAt(OffsetDateTime.now());
        u.setMustChangePassword(true);                 // mật khẩu do quản trị đặt = mật khẩu tạm
        refreshTokens.revokeAllOfUser(id, OffsetDateTime.now());
        audit.record("RESET_PASSWORD", "USER", id, null, null, null);
    }

    /**
     * Tài khoản cho chuyên gia khi import hồ sơ: vai trò EXPERT, mật khẩu tạm, bắt đổi ở lần đăng nhập đầu.
     * Người gọi (import) đã kiểm tra trùng tên đăng nhập / email trước để lỗi DB không làm hỏng transaction.
     */
    @Transactional
    public User createExpertAccount(String username, String email, String fullName, UUID departmentId,
                                    String position, String tempPassword) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setPosition(position);
        u.setDepartment(departmentId == null ? null : departments.findById(departmentId).orElse(null));
        u.setPasswordHash(passwordEncoder.encode(tempPassword));
        u.setPasswordChangedAt(OffsetDateTime.now());
        u.setMustChangePassword(true);
        u.setRoles(new HashSet<>(resolveRoles(List.of("EXPERT"))));
        u.setCreatedBy(SecurityUtils.currentUser().id());
        users.save(u);
        audit.record("CREATE", "USER", u.getId(), null, UserDto.from(u), "Tạo khi import hồ sơ chuyên gia");
        return u;
    }

    /** BR-1.1.2: chỉ soft delete. */
    @Transactional
    public void delete(UUID id) {
        User u = load(id);
        if (u.getId().equals(SecurityUtils.currentUser().id())) {
            throw ApiException.businessRule("Không thể tự xoá tài khoản của mình");
        }
        audit.record("DELETE", "USER", id, UserDto.from(u), null, null);
        u.setDeletedAt(OffsetDateTime.now());
        u.setStatus("DISABLED");
        u.getRoles().clear();                          // role không còn "đang được gán" bởi tài khoản đã xoá
        refreshTokens.revokeAllOfUser(id, OffsetDateTime.now());
    }

    private User load(UUID id) {
        return users.findById(id).filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Người dùng", id));
    }

    private List<Role> resolveRoles(List<String> codes) {
        List<Role> found = roles.findByCodeIn(codes);
        if (found.size() != new HashSet<>(codes).size()) {
            throw ApiException.badRequest("Có mã role không tồn tại: " + codes);
        }
        return found;
    }
}
