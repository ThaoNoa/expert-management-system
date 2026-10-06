package com.npcore.ems.expert;

import com.npcore.ems.shared.security.CurrentUser;
import com.npcore.ems.shared.security.SecurityUtils;
import com.npcore.ems.shared.web.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Phạm vi dữ liệu hồ sơ chuyên gia (BR-SOD-001/002):
 * quyền ":ALL" → mọi chuyên gia; chỉ có quyền (OWN) → duy nhất hồ sơ gắn với tài khoản đang đăng nhập.
 */
@Component
@RequiredArgsConstructor
public class ExpertAccess {

    private final ExpertRepository experts;

    public Expert requireView(UUID expertId) {
        return require(expertId, "EXPERT_VIEW");
    }

    public Expert requireEdit(UUID expertId) {
        return require(expertId, "EXPERT_EDIT");
    }

    public boolean editAll() {
        return SecurityUtils.currentUser().hasAll("EXPERT_EDIT");
    }

    private Expert require(UUID expertId, String permission) {
        CurrentUser u = SecurityUtils.currentUser();
        Expert e = experts.findById(expertId).filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Chuyên gia", expertId));
        if (u.hasAll(permission)) return e;
        if (u.has(permission) && u.id().equals(e.getUserId())) return e;
        if (u.has("EXPERT_VIEW") && u.id().equals(e.getUserId())) {
            throw ApiException.forbidden("Thiếu quyền " + permission);
        }
        throw ApiException.notFound("Chuyên gia", expertId);       // không lộ hồ sơ người khác
    }
}
