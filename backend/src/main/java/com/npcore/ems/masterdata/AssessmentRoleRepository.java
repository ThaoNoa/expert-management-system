package com.npcore.ems.masterdata;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRoleRepository extends JpaRepository<AssessmentRole, UUID> {
    Optional<AssessmentRole> findByCodeIgnoreCase(String code);
}
