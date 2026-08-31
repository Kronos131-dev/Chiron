package com.kronos.chiron.seance.persistence;

import com.kronos.chiron.seance.model.Exercice;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciceRepository extends JpaRepository<Exercice, Long> {

    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId AND LOWER(e.nom) LIKE LOWER(CONCAT('%', :nomExercice, '%')) ORDER BY e.startTime DESC")
    List<Exercice> findAllBySeanceUtilisateurIdAndNomContainingIgnoreCaseOrderByStartTimeDesc(
            @Param("utilisateurId") Long utilisateurId, @Param("nomExercice") String nomExercice);

    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId AND LOWER(e.nom) LIKE LOWER(CONCAT('%', :nomExercice, '%')) ORDER BY e.startTime DESC")
    List<Exercice> findTopBySeanceUtilisateurIdAndNomContainingIgnoreCase(@Param("utilisateurId") Long utilisateurId,
            @Param("nomExercice") String nomExercice, Pageable pageable);

    default Optional<Exercice> findFirstBySeanceUtilisateurIdAndNomContainingIgnoreCaseOrderByStartTimeDesc(
            Long utilisateurId, String nomExercice) {
        List<Exercice> results = findTopBySeanceUtilisateurIdAndNomContainingIgnoreCase(utilisateurId, nomExercice,
                PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId AND s.historique = true AND LOWER(e.nom) LIKE LOWER(CONCAT('%', :nomExercice, '%')) ORDER BY e.startTime DESC")
    List<Exercice> findAllHistoricExercises(@Param("utilisateurId") Long utilisateurId,
            @Param("nomExercice") String nomExercice);

    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId AND s.historique = true AND LOWER(e.nom) LIKE LOWER(CONCAT('%', :nomExercice, '%')) ORDER BY e.startTime DESC")
    List<Exercice> findTopHistoricExercises(@Param("utilisateurId") Long utilisateurId,
            @Param("nomExercice") String nomExercice, Pageable pageable);

    default Optional<Exercice> findFirstHistoricExercise(Long utilisateurId, String nomExercice) {
        List<Exercice> results = findTopHistoricExercises(utilisateurId, nomExercice, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId "
            + "AND s.historique = true AND e.definition.id = :definitionId AND s.startTime >= :since "
            + "ORDER BY e.startTime DESC")
    List<Exercice> findRecentHistoricExercisesByDefinition(
            @Param("utilisateurId") Long utilisateurId,
            @Param("definitionId") Long definitionId,
            @Param("since") java.time.LocalDateTime since);

    default Optional<Exercice> findLastPerformance(Long utilisateurId, Long definitionId,
            java.time.LocalDateTime since) {
        List<Exercice> results = findRecentHistoricExercisesByDefinition(utilisateurId, definitionId, since);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
