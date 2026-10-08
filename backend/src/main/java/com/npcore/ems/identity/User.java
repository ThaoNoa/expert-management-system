package com.npcore.ems.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID id;
    @Column(nullable = false, updatable = false)
    private String username;
    @Column(nullable = false)
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
    private String position;
    /** ACTIVE | DISABLED | LOCKED */
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;
    @Column(name = "mfa_secret")
    private String mfaSecret;
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;
    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;
    /** Mật khẩu tạm (import / quản trị đặt lại): bắt đổi ở lần đăng nhập tới. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
}
