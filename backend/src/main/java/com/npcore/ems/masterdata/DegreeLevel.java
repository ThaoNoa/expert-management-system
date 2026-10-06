package com.npcore.ems.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "degree_levels")
@Immutable
@Getter
public class DegreeLevel {
    @Id
    @Column(name = "degree_level_code")
    private String code;
    @Column(name = "degree_level_name")
    private String name;
    @Column(name = "rank_order")
    private short rankOrder;
}
