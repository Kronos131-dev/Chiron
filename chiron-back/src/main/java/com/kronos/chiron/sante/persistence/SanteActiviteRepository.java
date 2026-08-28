package com.kronos.chiron.sante.persistence;

import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SanteActiviteRepository extends JpaRepository<SanteActivite, Long> {

    Optional<SanteActivite> findBySeanceId(Long seanceId);

    // WHY: la relation SanteActivite.seance est LAZY et l'identifiant de Seance est mappé sur
    // le champ, donc Hibernate n'intercepte pas getId() sur le proxy : y toucher hors session,
    // comme le fait un tool de l'agent, lèverait une LazyInitializationException.
    @Query("select a.seance.id from SanteActivite a where a.id = :activiteId")
    Optional<Long> findSeanceIdByActiviteId(@Param("activiteId") Long activiteId);

    List<SanteActivite> findBySeanceIdIn(List<Long> seanceIds);

    List<SanteActivite> findByUtilisateurAndStartTimeBetweenOrderByStartTimeDesc(Utilisateur utilisateur,
            LocalDateTime from, LocalDateTime to);

    List<SanteActivite> findByStatutEnrichissementAndProchaineTentativeAtLessThanEqual(
            StatutEnrichissement statut, LocalDateTime maintenant);

    Optional<SanteActivite> findByUtilisateurAndStartTimeAndSource(Utilisateur utilisateur, LocalDateTime startTime,
            SourceActivite source);

    Optional<SanteActivite> findFirstByUtilisateurAndSourceAndStartTimeLessThanAndEndTimeGreaterThan(
            Utilisateur utilisateur, SourceActivite source, LocalDateTime endTimeExclusive,
            LocalDateTime startTimeExclusive);

    Optional<SanteActivite> findFirstByUtilisateurOrderByEndTimeDesc(Utilisateur utilisateur);

    List<SanteActivite> findByUtilisateurAndStatutEnrichissementAndEndTimeBetweenOrderByEndTimeDesc(
            Utilisateur utilisateur, StatutEnrichissement statut, LocalDateTime from, LocalDateTime to);

    List<SanteActivite> findByUtilisateurAndTypeActiviteAndStartTimeAfterOrderByStartTimeDesc(
            Utilisateur utilisateur, TypeActivite typeActivite, LocalDateTime after);

    List<SanteActivite> findByUtilisateurAndSourceAndStartTimeBetweenOrderByStartTimeAsc(Utilisateur utilisateur,
            SourceActivite source, LocalDateTime from, LocalDateTime to);

    List<SanteActivite> findByUtilisateurAndSourceAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
            Utilisateur utilisateur, SourceActivite source, LocalDateTime startTimeInclusive,
            LocalDateTime endTimeInclusive);
}
