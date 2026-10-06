package com.npcore.ems.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Một phiên bản bộ mã của scheme (BR-VER-002). Chỉ sửa khi DRAFT; mỗi scheme có tối đa 1 bộ ACTIVE. */
@Entity
@Table(name = "code_sets")
@Getter
@Setter
public class CodeSet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "code_set_id")
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_id")
    private Scheme scheme;
    @Column(nullable = false)
    private String version;
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
    @Column(name = "effective_to")
    private LocalDate effectiveTo;
    @Column(name = "source_ref")
    private String sourceRef;
    @Column(nullable = false)
    private String status = "DRAFT";
}
