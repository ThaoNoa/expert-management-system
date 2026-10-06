package com.npcore.ems.expert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "expert_languages")
@IdClass(ExpertLanguage.Key.class)
@Getter
@Setter
public class ExpertLanguage {
    @Id
    @Column(name = "expert_id")
    private UUID expertId;
    @Id
    private String language;
    /** BASIC | INTERMEDIATE | FLUENT | NATIVE */
    @Column(nullable = false)
    private String proficiency;
    @Column(name = "can_audit", nullable = false)
    private boolean canAudit;

    public record Key(UUID expertId, String language) implements Serializable {
        public Key() { this(null, null); }
    }
}
