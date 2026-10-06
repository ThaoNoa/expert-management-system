package com.npcore.ems.shared.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Tra chuyên gia gắn với tài khoản đăng nhập (phục vụ phạm vi dữ liệu OWN - BR-SOD-001/002). */
@Component
@RequiredArgsConstructor
public class ExpertOwnership {

    private final JdbcTemplate jdbc;

    public Optional<UUID> expertIdOf(UUID userId) {
        List<UUID> ids = jdbc.queryForList(
                "select expert_id from experts where user_id = ? and deleted_at is null", UUID.class, userId);
        return ids.stream().findFirst();
    }

    public Optional<UUID> currentExpertId() {
        return SecurityUtils.currentUserOptional().flatMap(u -> expertIdOf(u.id()));
    }
}
