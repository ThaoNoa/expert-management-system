package com.npcore.ems.identity;

import com.npcore.ems.config.EmsProperties;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.security.CurrentUser;
import com.npcore.ems.shared.security.JwtService;
import com.npcore.ems.shared.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PermissionRepository permissions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final AuditService audit;
    private final EmsProperties props;
    private final JdbcTemplate jdbc;

    public record MeDto(UUID id, String username, String fullName, String email, List<String> roles,
                        List<String> permissions, UUID expertId) {}

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, MeDto user) {}

    /** Không rollback khi ném lỗi để vẫn lưu số lần đăng nhập sai / trạng thái khoá. */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(String username, String password) {
        User user = users.findActiveByUsername(username).orElse(null);
        if (user == null) {
            audit.recordAs(null, username, "LOGIN_FAILED", "USER", null, "Không tồn tại");
            throw unauthorized("Sai tên đăng nhập hoặc mật khẩu");
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw unauthorized("Tài khoản đã bị vô hiệu hoá");           // BR-1.1.3
        }
        if ("LOCKED".equals(user.getStatus())) {
            throw unauthorized("Tài khoản bị khoá do đăng nhập sai nhiều lần, liên hệ quản trị viên");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= props.security().maxFailedLogins()) {
                user.setStatus("LOCKED");
            }
            audit.recordAs(user.getId(), user.getUsername(), "LOGIN_FAILED", "USER", user.getId(),
                    "Sai mật khẩu lần " + user.getFailedLoginCount());
            throw unauthorized("Sai tên đăng nhập hoặc mật khẩu");
        }
        user.setFailedLoginCount(0);
        user.setLastLoginAt(OffsetDateTime.now());
        audit.recordAs(user.getId(), user.getUsername(), "LOGIN", "USER", user.getId(), null);
        return issue(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(sha256(refreshToken))
                .orElseThrow(() -> unauthorized("Refresh token không hợp lệ"));
        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw unauthorized("Refresh token đã hết hạn");
        }
        token.setRevokedAt(OffsetDateTime.now());                       // rotate: mỗi refresh token dùng 1 lần
        User user = users.findById(token.getUserId()).orElseThrow(() -> unauthorized("Tài khoản không tồn tại"));
        if (!"ACTIVE".equals(user.getStatus()) || user.getDeletedAt() != null) {
            throw unauthorized("Tài khoản không còn hoạt động");
        }
        return issue(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(sha256(refreshToken)).ifPresent(t -> t.setRevokedAt(OffsetDateTime.now()));
    }

    @Transactional(readOnly = true)
    public MeDto me(CurrentUser current) {
        User user = users.findById(current.id()).orElseThrow(() -> unauthorized("Tài khoản không tồn tại"));
        return toMe(user, grants(user.getId()));
    }

    @Transactional
    public void changePassword(CurrentUser current, String currentPassword, String newPassword) {
        User user = users.findById(current.id()).orElseThrow(() -> unauthorized("Tài khoản không tồn tại"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }
        passwordPolicy.check(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(OffsetDateTime.now());
        refreshTokens.revokeAllOfUser(user.getId(), OffsetDateTime.now());
        audit.record("CHANGE_PASSWORD", "USER", user.getId(), null, null, null);
    }

    /** Tập quyền: mã quyền, cộng "MÃ:ALL" nếu có ít nhất một role cấp phạm vi toàn bộ. */
    Set<String> grants(UUID userId) {
        Set<String> result = new TreeSet<>();
        for (Object[] g : permissions.findGrantsOfUser(userId)) {
            String code = (String) g[0];
            result.add(code);
            if ("ALL".equals(g[1])) result.add(code + ":ALL");
        }
        return result;
    }

    private TokenResponse issue(User user) {
        Set<String> grants = grants(user.getId());
        String access = jwtService.issueAccessToken(user.getId(), user.getUsername(), grants);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(sha256(refresh));
        token.setIssuedAt(OffsetDateTime.now());
        token.setExpiresAt(OffsetDateTime.now().plusDays(props.security().refreshTokenDays()));
        refreshTokens.save(token);
        return new TokenResponse(access, refresh, jwtService.accessTtlSeconds(), toMe(user, grants));
    }

    private MeDto toMe(User user, Set<String> grants) {
        List<UUID> expert = jdbc.queryForList(
                "select expert_id from experts where user_id = ? and deleted_at is null", UUID.class, user.getId());
        return new MeDto(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getRoles().stream().map(Role::getCode).sorted().toList(), List.copyOf(grants),
                expert.isEmpty() ? null : expert.get(0));
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
