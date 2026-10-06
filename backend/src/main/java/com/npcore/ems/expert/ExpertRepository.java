package com.npcore.ems.expert;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ExpertRepository extends JpaRepository<Expert, UUID>, JpaSpecificationExecutor<Expert> {
    boolean existsByCode(String code);

    @Query("select e from Expert e where e.userId = ?1 and e.deletedAt is null")
    Optional<Expert> findByUserId(UUID userId);

    java.util.List<Expert> findByStatusAndSuspendedUntilBeforeAndDeletedAtIsNull(String status, java.time.LocalDate date);

    @Query("select count(e) > 0 from Expert e where e.userId = ?1 and e.id <> ?2")
    boolean userLinkedElsewhere(UUID userId, UUID expertId);
}
