package com.kronos.chiron.sante.persistence;

import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SanteSommeilRepository extends JpaRepository<SanteSommeil, Long> {

    Optional<SanteSommeil> findByUtilisateurAndDebut(Utilisateur utilisateur, LocalDateTime debut);

    List<SanteSommeil> findByUtilisateurAndDateBetweenOrderByDebutAsc(Utilisateur utilisateur, LocalDate from,
            LocalDate to);

    Optional<SanteSommeil> findFirstByUtilisateurAndDateOrderByFinDesc(Utilisateur utilisateur, LocalDate date);
}
