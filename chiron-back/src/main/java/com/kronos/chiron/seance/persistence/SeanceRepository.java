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

    List<Seance> findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(String username);

    List<Seance> findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(String username);

    @Query("SELECT COUNT(s) FROM Serie s JOIN s.exercice e JOIN e.seance se WHERE se.utilisateur.id = :userId AND se.isModele = true AND se.startTime >= :startDate")
    Integer countTotalSeriesForUserSince(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
}
