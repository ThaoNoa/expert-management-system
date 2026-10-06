package com.npcore.ems.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentLinkRepository extends JpaRepository<DocumentLink, UUID> {
    List<DocumentLink> findByDocumentIdOrderByLinkedAtDesc(UUID documentId);

    boolean existsByDocumentIdAndObjectTypeAndObjectId(UUID documentId, String objectType, UUID objectId);
}
