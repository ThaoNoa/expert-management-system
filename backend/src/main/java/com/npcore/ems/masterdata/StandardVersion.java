package com.npcore.ems.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "standard_versions")
@Getter
@Setter
public class StandardVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "standard_version_id")
    private UUID id;
    @Column(name = "standard_id", nullable = false)
    private UUID standardId;
    @Column(nullable = false)
    private String version;
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
    @Column(name = "effective_to")
    private LocalDate effectiveTo;
    @Column(name = "transition_end")
    private LocalDate transitionEnd;
    @Column(nullable = false)
    private String status = "ACTIVE";
}
