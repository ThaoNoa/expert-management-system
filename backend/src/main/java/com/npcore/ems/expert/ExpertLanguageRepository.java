package com.npcore.ems.expert;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertLanguageRepository extends JpaRepository<ExpertLanguage, ExpertLanguage.Key> {
    List<ExpertLanguage> findByExpertIdOrderByLanguage(UUID expertId);
}
