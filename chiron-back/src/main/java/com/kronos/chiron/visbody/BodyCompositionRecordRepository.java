package com.kronos.chiron.visbody;

import com.kronos.chiron.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Accès aux scans de composition corporelle Visbody.
 */
@Repository
public interface BodyCompositionRecordRepository extends JpaRepository<BodyCompositionRecord, Long> {

    /** Historique chronologique (ascendant) des scans d'un utilisateur. */
    List<BodyCompositionRecord> findByUtilisateurOrderByMesureLeAsc(Utilisateur utilisateur);

    /** Scans d'un utilisateur depuis une date donnée (ascendant). */
    List<BodyCompositionRecord> findByUtilisateurAndMesureLeAfterOrderByMesureLeAsc(
            Utilisateur utilisateur, LocalDateTime since);

    /** Vrai si ce scan (même instant) a déjà été importé pour cet utilisateur. */
    boolean existsByUtilisateurAndMesureLe(Utilisateur utilisateur, LocalDateTime mesureLe);
}
