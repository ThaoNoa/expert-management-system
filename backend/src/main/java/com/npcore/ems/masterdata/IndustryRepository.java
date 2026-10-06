package com.npcore.ems.masterdata;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndustryRepository extends JpaRepository<Industry, UUID> {
    Optional<Industry> findByCodeIgnoreCase(String code);
}
