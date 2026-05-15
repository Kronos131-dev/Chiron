package com.kronos.chiron.repository;

import com.kronos.chiron.entity.Exercice;
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
    List<Exercice> findAllBySeanceUtilisateurIdAndNomContainingIgnoreCaseOrderByStartTimeDesc(@Param("utilisateurId") Long utilisateurId, @Param("nomExercice") String nomExercice);

    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId AND LOWER(e.nom) LIKE LOWER(CONCAT('%', :nomExercice, '%')) ORDER BY e.startTime DESC")
    List<Exercice> findTopBySeanceUtilisateurIdAndNomContainingIgnoreCase(@Param("utilisateurId") Long utilisateurId, @Param("nomExercice") String nomExercice, Pageable pageable);

    default Optional<Exercice> findFirstBySeanceUtilisateurIdAndNomContainingIgnoreCaseOrderByStartTimeDesc(Long utilisateurId, String nomExercice) {
        List<Exercice> results = findTopBySeanceUtilisateurIdAndNomContainingIgnoreCase(utilisateurId, nomExercice, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId AND s.isModele = true AND LOWER(e.nom) LIKE LOWER(CONCAT('%', :nomExercice, '%')) ORDER BY e.startTime DESC")
    List<Exercice> findAllHistoricExercises(@Param("utilisateurId") Long utilisateurId, @Param("nomExercice") String nomExercice);

    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId AND s.isModele = true AND LOWER(e.nom) LIKE LOWER(CONCAT('%', :nomExercice, '%')) ORDER BY e.startTime DESC")
    List<Exercice> findTopHistoricExercises(@Param("utilisateurId") Long utilisateurId, @Param("nomExercice") String nomExercice, Pageable pageable);

    default Optional<Exercice> findFirstHistoricExercise(Long utilisateurId, String nomExercice) {
        List<Exercice> results = findTopHistoricExercises(utilisateurId, nomExercice, PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
