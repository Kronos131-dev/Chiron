package com.kronos.chiron.noctua.persistence;

import com.kronos.chiron.noctua.model.NoctuaBriefing;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NoctuaBriefingRepository extends JpaRepository<NoctuaBriefing, Long> {

    List<NoctuaBriefing> findByUtilisateurAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Utilisateur utilisateur,
            LocalDateTime depuis);

    Optional<NoctuaBriefing> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);

    boolean existsByUtilisateurAndCleDeclencheur(Utilisateur utilisateur, String cleDeclencheur);

    long countByUtilisateurAndLuFalse(Utilisateur utilisateur);
}
