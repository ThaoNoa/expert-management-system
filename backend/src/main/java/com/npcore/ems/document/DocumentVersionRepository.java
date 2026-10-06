package com.npcore.ems.document;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    Optional<DocumentVersion> findBySha256(String sha256);

    List<DocumentVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId);

    List<DocumentVersion> findByIdIn(Collection<UUID> ids);
}
