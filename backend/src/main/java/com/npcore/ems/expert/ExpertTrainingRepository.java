package com.npcore.ems.expert;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertTrainingRepository extends JpaRepository<ExpertTraining, UUID> {
    List<ExpertTraining> findByExpertId(UUID expertId);

    long countByExpertId(UUID expertId);
}
