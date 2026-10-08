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

    /** Trùng định nghĩa: cùng tiêu chuẩn + code (kể cả cùng null = toàn tiêu chuẩn) + vai trò + phiên bản. */
    @Query("SELECT COUNT(cd) > 0 FROM CompetencyDefinition cd WHERE cd.standard.id = :standardId " +
           "AND ((:codeId IS NULL AND cd.code IS NULL) OR cd.code.id = :codeId) " +
           "AND cd.assessmentRole.id = :roleId AND cd.version = :version AND (:excludeId IS NULL OR cd.id <> :excludeId)")
    boolean duplicateExists(@Param("standardId") UUID standardId, @Param("codeId") UUID codeId,
                            @Param("roleId") UUID roleId, @Param("version") String version,
                            @Param("excludeId") UUID excludeId);

    /** Định nghĩa đang hiệu lực cho tổ hợp tiêu chuẩn + code + vai trò (dùng khi đăng ký năng lực). */
    @Query("SELECT cd FROM CompetencyDefinition cd WHERE cd.standard.id = :standardId " +
           "AND ((:codeId IS NULL AND cd.code IS NULL) OR cd.code.id = :codeId) " +
           "AND cd.assessmentRole.id = :roleId AND cd.status = 'ACTIVE' ORDER BY cd.effectiveFrom DESC")
    List<CompetencyDefinition> findActiveFor(@Param("standardId") UUID standardId, @Param("codeId") UUID codeId,
                                             @Param("roleId") UUID roleId);
}
