package com.npcore.ems.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** Danh mục quyền do migration quản lý (seed V12), không sửa qua API. */
@Entity
@Table(name = "permissions")
@Immutable
@Getter
public class Permission {
    @Id
    @Column(name = "permission_id")
    private UUID id;
    @Column(name = "permission_code")
    private String code;
    @Column(name = "permission_name")
    private String name;
    private String module;
    private String description;
}
