package com.npcore.ems.expert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Quy tắc sinh mã chuyên gia FT-xxxx / PT-xxx theo cấu hình (BR-2.1.2). */
@Entity
@Table(name = "expert_code_sequences")
@Getter
@Setter
public class ExpertCodeSequence {
    @Id
    @Column(name = "employment_type")
    private String employmentType;
    @Column(nullable = false)
    private String prefix;
    @Column(name = "pad_length", nullable = false)
    private short padLength;
    @Column(name = "next_value", nullable = false)
    private long nextValue;
}
