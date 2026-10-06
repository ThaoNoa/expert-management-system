package com.npcore.ems.expert;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ExpertCodeSequenceRepository extends JpaRepository<ExpertCodeSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExpertCodeSequence s where s.employmentType = ?1")
    Optional<ExpertCodeSequence> lockByEmploymentType(String employmentType);
}
