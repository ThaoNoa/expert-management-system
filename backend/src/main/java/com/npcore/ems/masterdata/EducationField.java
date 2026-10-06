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

@Entity
@Table(name = "education_fields")
@Getter
@Setter
public class EducationField {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "field_id")
    private UUID id;
    @Column(name = "field_code", nullable = false)
    private String code;
    @Column(name = "field_name", nullable = false)
    private String name;
    @Column(name = "parent_id")
    private UUID parentId;
}
