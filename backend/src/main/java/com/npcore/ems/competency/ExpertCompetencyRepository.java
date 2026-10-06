package com.npcore.ems.competency;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpertCompetencyRepository extends JpaRepository<ExpertCompetency, UUID>,
        JpaSpecificationExecutor<ExpertCompetency> {

    @Query("SELECT ec FROM ExpertCompetency ec " +
           "JOIN FETCH ec.competencyDefinition cd " +
           "JOIN FETCH cd.scheme " +
           "JOIN FETCH cd.standard " +
           "LEFT JOIN FETCH cd.code " +
           "JOIN FETCH cd.assessmentRole " +
           "WHERE ec.expert.id = :expertId " +
           "AND (:status IS NULL OR ec.status = :status)")
    Page<ExpertCompetency> findByExpertIdAndStatusWithDetails(
            @Param("expertId") UUID expertId,
            @Param("status") String status,
            Pageable pageable);

    @Query("SELECT ec FROM ExpertCompetency ec " +
           "JOIN FETCH ec.competencyDefinition cd " +
           "JOIN FETCH cd.scheme " +
           "JOIN FETCH cd.standard " +
           "LEFT JOIN FETCH cd.code " +
           "JOIN FETCH cd.assessmentRole " +
           "WHERE ec.expert.id = :expertId")
    List<ExpertCompetency> findByExpertIdWithDetails(@Param("expertId") UUID expertId);

    @Query("SELECT ec FROM ExpertCompetency ec " +
           "JOIN FETCH ec.competencyDefinition cd " +
           "JOIN FETCH cd.scheme " +
           "JOIN FETCH cd.standard " +
           "LEFT JOIN FETCH cd.code " +
           "JOIN FETCH cd.assessmentRole " +
           "WHERE ec.id = :id")
    Optional<ExpertCompetency> findByIdWithDetails(@Param("id") UUID id);

    boolean existsByExpertIdAndCompetencyDefinitionIdAndStatusIn(
            UUID expertId, UUID definitionId, Collection<String> statuses);

    @Query("SELECT ec FROM ExpertCompetency ec " +
           "JOIN FETCH ec.expert e " +
           "JOIN FETCH ec.competencyDefinition cd " +
           "JOIN FETCH cd.standard s " +
           "LEFT JOIN FETCH cd.code c " +
           "JOIN FETCH cd.assessmentRole ar " +
           "WHERE s.id = :standardId AND ec.status IN ('APPROVED', 'REVIEW_REQUIRED')")
    List<ExpertCompetency> findApprovedByStandardId(@Param("standardId") UUID standardId);
}
