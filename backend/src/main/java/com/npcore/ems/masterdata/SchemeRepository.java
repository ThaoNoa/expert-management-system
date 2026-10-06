package com.npcore.ems.masterdata;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemeRepository extends JpaRepository<Scheme, UUID> {
    Optional<Scheme> findByCodeIgnoreCase(String code);
}
