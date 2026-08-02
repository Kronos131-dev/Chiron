package com.kronos.chiron.visbody;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BodyCompositionRecordRepository extends JpaRepository<BodyCompositionRecord, Long> {

    List<BodyCompositionRecord> findByUtilisateurOrderByMesureLeAsc(Utilisateur utilisateur);

    List<BodyCompositionRecord> findByUtilisateurAndMesureLeAfterOrderByMesureLeAsc(
            Utilisateur utilisateur, LocalDateTime since);

    boolean existsByUtilisateurAndMesureLe(Utilisateur utilisateur, LocalDateTime mesureLe);
}
