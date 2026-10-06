package com.npcore.ems.competency;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetencyDefinitionRepository extends JpaRepository<CompetencyDefinition, UUID>,
        JpaSpecificationExecutor<CompetencyDefinition> {

    @Query("SELECT cd FROM CompetencyDefinition cd " +
           "JOIN FETCH cd.scheme " +
           "JOIN FETCH cd.standard " +
           "LEFT JOIN FETCH cd.code " +
           "JOIN FETCH cd.assessmentRole " +
           "WHERE (:status IS NULL OR cd.status = :status)")
    List<CompetencyDefinition> findAllWithDetails(@Param("status") String status);

    @Query("SELECT cd FROM CompetencyDefinition cd " +
           "JOIN FETCH cd.scheme " +
           "JOIN FETCH cd.standard " +
           "LEFT JOIN FETCH cd.code " +
           "JOIN FETCH cd.assessmentRole " +
           "WHERE cd.standard.id = :standardId AND (:status IS NULL OR cd.status = :status)")
    List<CompetencyDefinition> findByStandardIdWithDetails(@Param("standardId") UUID standardId, @Param("status") String status);

    boolean existsByStandardIdAndCodeIdAndAssessmentRoleIdAndVersion(
            UUID standardId, UUID codeId, UUID assessmentRoleId, String version);
}
