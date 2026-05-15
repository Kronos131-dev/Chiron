package com.kronos.chiron.repository;

import com.kronos.chiron.entity.ExerciseType;
import com.kronos.chiron.entity.PerformanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PerformanceRecordRepository extends JpaRepository<PerformanceRecord, Long> {

    List<PerformanceRecord> findByUtilisateurIdOrderByRecordedAtDesc(Long utilisateurId);

    List<PerformanceRecord> findByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(
            Long utilisateurId, ExerciseType exerciseType);

    Optional<PerformanceRecord> findFirstByUtilisateurIdAndExerciseTypeOrderByRm1EstimeDesc(
            Long utilisateurId, ExerciseType exerciseType);

    Optional<PerformanceRecord> findFirstByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(
            Long utilisateurId, ExerciseType exerciseType);

    // Single query replacing N individual findFirst calls in the performance summary
    @Query("""
            SELECT pr FROM PerformanceRecord pr
            WHERE pr.utilisateur.id = :userId
            AND pr.recordedAt = (
                SELECT MAX(pr2.recordedAt) FROM PerformanceRecord pr2
                WHERE pr2.utilisateur.id = :userId AND pr2.exerciseType = pr.exerciseType
            )
            """)
    List<PerformanceRecord> findLatestPerExerciseTypeForUser(@Param("userId") Long userId);
}
