package com.npcore.ems.expert;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertCertificateRepository extends JpaRepository<ExpertCertificate, UUID> {
    List<ExpertCertificate> findByExpertId(UUID expertId);

    long countByExpertId(UUID expertId);
}
