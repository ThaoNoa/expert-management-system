package com.npcore.ems.masterdata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CodeRepository extends JpaRepository<Code, UUID> {
    List<Code> findByCodeSetIdOrderByPath(UUID codeSetId);

    Optional<Code> findByCodeSetIdAndValueIgnoreCase(UUID codeSetId, String value);

    @Query("select c.codeSetId, count(c) from Code c group by c.codeSetId")
    List<Object[]> countBySet();
}
