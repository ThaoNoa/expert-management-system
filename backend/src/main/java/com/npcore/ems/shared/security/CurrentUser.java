package com.npcore.ems.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * Người dùng đang đăng nhập (lấy từ JWT).
 * permissions chứa mã quyền ("EXPERT_VIEW") và mã kèm phạm vi toàn bộ ("EXPERT_VIEW:ALL").
 */
public record CurrentUser(UUID id, String username, Set<String> permissions) {

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    /** Có quyền trên toàn bộ dữ liệu (không chỉ dữ liệu của mình). */
    public boolean hasAll(String permission) {
        return permissions.contains(permission + ":ALL");
    }
}
