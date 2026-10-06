package com.npcore.ems.masterdata;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandardVersionRepository extends JpaRepository<StandardVersion, UUID> {
    List<StandardVersion> findByStandardIdOrderByEffectiveFromDesc(UUID standardId);

    boolean existsByStandardIdAndVersionIgnoreCase(UUID standardId, String version);
}
