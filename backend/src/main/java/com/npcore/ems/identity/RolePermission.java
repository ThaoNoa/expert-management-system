package com.npcore.ems.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RolePermission {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private Key id = new Key();

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @MapsId("permissionId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    /** ALL | DEPARTMENT | OWN (BR-SOD-001/002). */
    @Column(name = "data_scope", nullable = false)
    private String dataScope = "ALL";

    public RolePermission(Role role, Permission permission, String dataScope) {
        this.role = role;
        this.permission = permission;
        this.dataScope = dataScope;
        this.id = new Key(role.getId(), permission.getId());
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "role_id")
        private UUID roleId;
        @Column(name = "permission_id")
        private UUID permissionId;

        public Key(UUID roleId, UUID permissionId) {
            this.roleId = roleId;
            this.permissionId = permissionId;
        }
    }
}
