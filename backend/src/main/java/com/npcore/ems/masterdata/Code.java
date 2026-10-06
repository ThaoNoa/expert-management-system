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

/** Nút trong cây Code. path = "/A/AI/" để truy vấn cây nhanh (code cha bao code con). */
@Entity
@Table(name = "codes")
@Getter
@Setter
public class Code {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "code_id")
    private UUID id;
    @Column(name = "code_set_id", nullable = false)
    private UUID codeSetId;
    @Column(name = "code_value", nullable = false)
    private String value;
    @Column(name = "code_name", nullable = false)
    private String name;
    @Column(name = "parent_id")
    private UUID parentId;
    @Column(nullable = false)
    private short level = 1;
    @Column(nullable = false)
    private String path;
    @Column(name = "risk_category")
    private String riskCategory;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "replaces_code_id")
    private UUID replacesCodeId;
}
