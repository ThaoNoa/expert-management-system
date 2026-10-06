package com.npcore.ems.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Một tài liệu dùng cho nhiều đối tượng, không upload lại (BR-12.1.2, BR-2.7.2). */
@Entity
@Table(name = "document_links")
@Getter
@Setter
public class DocumentLink {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "link_id")
    private UUID id;
    @Column(name = "document_id", nullable = false)
    private UUID documentId;
    @Column(name = "linked_object_type", nullable = false)
    private String objectType;
    @Column(name = "linked_object_id", nullable = false)
    private UUID objectId;
    private String purpose;
    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;
    @Column(name = "linked_by")
    private UUID linkedBy;
}
