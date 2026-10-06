package com.npcore.ems.competency;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyEvidenceRepository extends JpaRepository<CompetencyEvidence, UUID> {
    List<CompetencyEvidence> findByExpertCompetencyId(UUID expertCompetencyId);
}
