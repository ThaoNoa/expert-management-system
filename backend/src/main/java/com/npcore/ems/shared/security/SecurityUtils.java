package com.npcore.ems.shared.security;

import com.npcore.ems.shared.web.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static Optional<CurrentUser> currentUserOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
            return Optional.of(cu);
        }
        return Optional.empty();
    }

    public static CurrentUser currentUser() {
        return currentUserOptional().orElseThrow(() -> ApiException.forbidden("Chưa đăng nhập"));
    }

    public static void require(String permission) {
        if (!currentUser().has(permission)) {
            throw ApiException.forbidden("Thiếu quyền " + permission);
        }
    }
}
