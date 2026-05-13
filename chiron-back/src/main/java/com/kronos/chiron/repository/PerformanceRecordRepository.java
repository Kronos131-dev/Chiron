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
}
