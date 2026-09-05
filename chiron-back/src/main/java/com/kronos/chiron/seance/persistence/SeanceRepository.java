package com.kronos.chiron.seance.persistence;

import com.kronos.chiron.seance.model.Seance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    // WHY: le push vers Google Health tourne en @Async, donc sans session Hibernate : lire
    // seance.getUtilisateur().getUsername() y déclenche une LazyInitializationException. Cette
    // projection rend le nom sans jamais initialiser le proxy.
    @Query("SELECT se.utilisateur.username FROM Seance se WHERE se.id = :seanceId")
    Optional<String> findUsernameBySeanceId(@Param("seanceId") Long seanceId);
}
