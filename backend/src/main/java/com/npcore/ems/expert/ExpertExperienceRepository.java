package com.npcore.ems.expert;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertExperienceRepository extends JpaRepository<ExpertExperience, UUID> {
    List<ExpertExperience> findByExpertId(UUID expertId);

    long countByExpertId(UUID expertId);
}
