package com.npcore.ems.masterdata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StandardRepository extends JpaRepository<Standard, UUID> {
    Optional<Standard> findByCodeIgnoreCase(String code);

    @Query("select s from Standard s join fetch s.scheme where (?1 is null or s.scheme.id = ?1) order by s.code")
    List<Standard> search(UUID schemeId);
}
