package com.kronos.chiron.repository;

import com.kronos.chiron.entity.Exercice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing Exercice entities.
 * Provides custom queries to fetch user-specific exercises based on names and history.
 */
@Repository
public interface ExerciceRepository extends JpaRepository<Exercice, Long> {

    /**
     * Finds all exercises matching a given name for a specific user, ordered by start time descending.
     *
     * @param utilisateurId The ID of the user.
     * @param nomExercice   The partial or full name of the exercise to search for (case-insensitive).
     * @return A list of matching Exercice entities.
     */
    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId AND LOWER(e.nom) LIKE LOWER(CONCAT('%', :nomExercice, '%')) ORDER BY e.startTime DESC")
    List<Exercice> findAllBySeanceUtilisateurIdAndNomContainingIgnoreCaseOrderByStartTimeDesc(@Param("utilisateurId") Long utilisateurId, @Param("nomExercice") String nomExercice);

    /**
     * Retrieves the most recent exercise matching a given name for a specific user.
     *
     * @param utilisateurId The ID of the user.
     * @param nomExercice   The partial or full name of the exercise to search for (case-insensitive).
     * @return An Optional containing the most recent matching Exercice, if one exists.
     */
    default Optional<Exercice> findFirstBySeanceUtilisateurIdAndNomContainingIgnoreCaseOrderByStartTimeDesc(Long utilisateurId, String nomExercice) {
        List<Exercice> results = findAllBySeanceUtilisateurIdAndNomContainingIgnoreCaseOrderByStartTimeDesc(utilisateurId, nomExercice);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds all historical exercises (from sessions where isModele = true) matching a given name for a specific user.
     *
     * @param utilisateurId The ID of the user.
     * @param nomExercice   The partial or full name of the exercise to search for (case-insensitive).
     * @return A list of matching historical Exercice entities.
     */
    @Query("SELECT e FROM Exercice e JOIN e.seance s WHERE s.utilisateur.id = :utilisateurId AND s.isModele = true AND LOWER(e.nom) LIKE LOWER(CONCAT('%', :nomExercice, '%')) ORDER BY e.startTime DESC")
    List<Exercice> findAllHistoricExercises(@Param("utilisateurId") Long utilisateurId, @Param("nomExercice") String nomExercice);

    /**
     * Retrieves the most recent historical exercise matching a given name for a specific user.
     *
     * @param utilisateurId The ID of the user.
     * @param nomExercice   The partial or full name of the exercise to search for (case-insensitive).
     * @return An Optional containing the most recent matching historical Exercice, if one exists.
     */
    default Optional<Exercice> findFirstHistoricExercise(Long utilisateurId, String nomExercice) {
        List<Exercice> results = findAllHistoricExercises(utilisateurId, nomExercice);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
