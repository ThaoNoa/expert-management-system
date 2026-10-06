package com.npcore.ems.identity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findByCodeIn(Collection<String> codes);

    /** Mã quyền + phạm vi dữ liệu của user, gom từ mọi role. */
    @Query("select rp.permission.code, rp.dataScope from User u join u.roles r join r.permissions rp where u.id = ?1")
    List<Object[]> findGrantsOfUser(UUID userId);
}
