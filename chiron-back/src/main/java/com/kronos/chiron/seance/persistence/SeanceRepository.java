package com.kronos.chiron.seance.persistence;

import com.kronos.chiron.seance.model.Seance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeanceRepository extends JpaRepository<Seance, Long> {

    List<Seance> findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(Long utilisateurId, int weekNumber);

    Optional<Seance> findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(Long utilisateurId);

    List<Seance> findByUtilisateurUsernameAndHistoriqueTrueOrderByStartTimeDesc(String username);

    List<Seance> findByUtilisateurUsernameAndHistoriqueTrueAndExercicesIsNotEmptyOrderByStartTimeDesc(
            String username);

    List<Seance> findByUtilisateurUsernameAndHistoriqueFalseOrderByDisplayOrderAscStartTimeDesc(String username);

    @Query("SELECT COUNT(s) FROM Serie s JOIN s.exercice e JOIN e.seance se WHERE se.utilisateur.id = :userId AND se.historique = true AND se.startTime >= :startDate")
    Integer countTotalSeriesForUserSince(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);

    @EntityGraph(attributePaths = "exercices")
    @Query("""
            SELECT DISTINCT s FROM Seance s
            WHERE s.historique = true AND s.endTime IS NOT NULL AND s.modeleId IN :modeleIds
            ORDER BY s.startTime ASC
            """)
    List<Seance> findCompletesParModele(@Param("modeleIds") Collection<Long> modeleIds);

    @EntityGraph(attributePaths = "exercices")
    @Query("""
            SELECT DISTINCT s FROM Seance s
            WHERE s.historique = true AND s.endTime IS NOT NULL
              AND s.utilisateur.id = :utilisateurId AND s.id IN :ids
            ORDER BY s.startTime ASC
            """)
    List<Seance> findCompletesDeLUtilisateur(@Param("utilisateurId") Long utilisateurId,
            @Param("ids") Collection<Long> ids);

    @Query("""
            SELECT s.id FROM Seance s
            WHERE s.historique = true AND s.endTime IS NOT NULL AND s.exercices IS NOT EMPTY
              AND s.modeleId IN :modeleIds
            """)
    List<Long> findIdsCompletesParModele(@Param("modeleIds") Collection<Long> modeleIds);

    @Query("""
            SELECT s.id AS id, s.titre AS titre FROM Seance s
            WHERE s.historique = true AND s.endTime IS NOT NULL AND s.exercices IS NOT EMPTY
              AND s.modeleId IS NULL AND s.utilisateur.id = :utilisateurId
            """)
    List<SeanceTitre> findTitresCompletesSansModele(@Param("utilisateurId") Long utilisateurId);

    // WHY: le push vers Google Health tourne en @Async, donc sans session Hibernate : lire
    // seance.getUtilisateur().getUsername() y déclenche une LazyInitializationException. Cette
    // projection rend le nom sans jamais initialiser le proxy.
    @Query("SELECT se.utilisateur.username FROM Seance se WHERE se.id = :seanceId")
    Optional<String> findUsernameBySeanceId(@Param("seanceId") Long seanceId);
}
