package com.npcore.ems.masterdata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CodeSetRepository extends JpaRepository<CodeSet, UUID> {
    @Query("select c from CodeSet c join fetch c.scheme where (?1 is null or c.scheme.id = ?1) order by c.scheme.code, c.effectiveFrom desc")
    List<CodeSet> search(UUID schemeId);

    Optional<CodeSet> findBySchemeIdAndStatus(UUID schemeId, String status);

    boolean existsBySchemeIdAndVersionIgnoreCase(UUID schemeId, String version);
}
