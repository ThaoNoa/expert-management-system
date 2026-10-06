package com.npcore.ems.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.masterdata.AssessmentRole;
import com.npcore.ems.masterdata.Code;
import com.npcore.ems.masterdata.Scheme;
import com.npcore.ems.masterdata.Standard;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Định nghĩa năng lực (Competency Definition): Catalog tổ hợp Scheme + Standard + Code + Assessment Role.
 * Code có thể null (áp dụng cho toàn tiêu chuẩn, vd Lead Auditor).
 */
@Entity
@Table(name = "competency_definitions")
@Getter
@Setter
public class CompetencyDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "competency_definition_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "standard_id", nullable = false)
    private Standard standard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_id")
    private Code code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_role_id", nullable = false)
    private AssessmentRole assessmentRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode criteria;

    @Column(name = "default_validity_months")
    private Short defaultValidityMonths;

    @Column(nullable = false)
    private String version = "1";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "sop_id")
    private UUID sopId;

    @Column(nullable = false)
    private String status = "ACTIVE";
}
