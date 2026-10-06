package com.npcore.ems.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Vai trò trong đoàn đánh giá (LA/AU/TE/OBS/TRAINEE) - tách biệt hoàn toàn với System Role (BR-1.5.1). */
@Entity
@Table(name = "assessment_roles")
@Getter
@Setter
public class AssessmentRole {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assessment_role_id")
    private UUID id;
    @Column(name = "role_code", nullable = false)
    private String code;
    @Column(name = "role_name", nullable = false)
    private String name;
    private String description;
    @Column(name = "counts_for_coverage", nullable = false)
    private boolean countsForCoverage = true;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private String status = "ACTIVE";
}
