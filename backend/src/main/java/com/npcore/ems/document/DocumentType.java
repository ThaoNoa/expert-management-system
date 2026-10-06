package com.npcore.ems.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "document_types")
@Immutable
@Getter
public class DocumentType {
    @Id
    @Column(name = "document_type_code")
    private String code;
    @Column(name = "document_type_name")
    private String name;
    @Column(name = "requires_expiry")
    private boolean requiresExpiry;
    @Column(name = "requires_verification")
    private boolean requiresVerification;
}
