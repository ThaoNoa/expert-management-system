package com.npcore.ems.masterdata;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationFieldRepository extends JpaRepository<EducationField, UUID> {
    Optional<EducationField> findByCodeIgnoreCase(String code);
}
