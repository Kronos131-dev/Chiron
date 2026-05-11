package com.kronos.chiron.repository;

import com.kronos.chiron.entity.Seance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing Seance entities.
 * Provides specialized queries to retrieve user workout sessions, templates, and perform statistical calculations.
 */
@Repository
public interface SeanceRepository extends JpaRepository<Seance, Long> {

    /**
     * Finds all workout sessions for a specific user within a given week, ordered by start time descending.
     *
     * @param utilisateurId The ID of the user.
     * @param weekNumber    The specific week number.
     * @return A list of matching Seance entities.
     */
    List<Seance> findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(Long utilisateurId, int weekNumber);

    /**
     * Retrieves the most recent active (unfinished) workout session for a user.
     *
     * @param utilisateurId The ID of the user.
     * @return An Optional containing the active Seance, if one exists.
     */
    Optional<Seance> findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(Long utilisateurId);

    /**
     * Retrieves all historical workout sessions (isModele = true) for a specific user, ordered by start time descending.
     *
     * @param username The username of the user.
     * @return A list of historical Seance entities.
     */
    List<Seance> findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(String username);

    /**
     * Retrieves all workout templates/programs (isModele = false) for a specific user, ordered by start time descending.
     *
     * @param username The username of the user.
     * @return A list of template Seance entities.
     */
    List<Seance> findByUtilisateurUsernameAndIsModeleFalseOrderByStartTimeDesc(String username);

    /**
     * Calculates the total number of sets (series) performed by a user in historical sessions
     * (isModele = true) since a specified date. Primarily used for rank calculation.
     *
     * @param userId    The ID of the user.
     * @param startDate The start date to begin counting from.
     * @return The total count of series performed.
     */
    @Query("SELECT COUNT(s) FROM Serie s JOIN s.exercice e JOIN e.seance se WHERE se.utilisateur.id = :userId AND se.isModele = true AND se.startTime >= :startDate")
    Integer countTotalSeriesForUserSince(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
}
