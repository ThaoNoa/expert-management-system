package com.npcore.ems.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "schemes")
@Getter
@Setter
public class Scheme {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "scheme_id")
    private UUID id;
    @Column(name = "scheme_code", nullable = false)
    private String code;
    @Column(name = "scheme_name", nullable = false)
    private String name;
    private String description;
    /** BR-3.1.3 / BR-COV-005: code cha có bao phủ code con hay không, cấu hình theo scheme. */
    @Column(name = "parent_covers_child", nullable = false)
    private boolean parentCoversChild;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
}
