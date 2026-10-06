package com.npcore.ems.expert;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertEducationRepository extends JpaRepository<ExpertEducation, UUID> {
    List<ExpertEducation> findByExpertId(UUID expertId);

    long countByExpertId(UUID expertId);
}
