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
@Table(name = "industries")
@Getter
@Setter
public class Industry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "industry_id")
    private UUID id;
    @Column(name = "industry_code", nullable = false)
    private String code;
    @Column(name = "industry_name", nullable = false)
    private String name;
    private String description;
}
