package com.kronos.chiron.sante.persistence;

import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SanteJourRepository extends JpaRepository<SanteJour, Long> {

    Optional<SanteJour> findByUtilisateurAndDate(Utilisateur utilisateur, LocalDate date);

    List<SanteJour> findByUtilisateurAndDateBetweenOrderByDateAsc(Utilisateur utilisateur, LocalDate from,
            LocalDate to);
}
