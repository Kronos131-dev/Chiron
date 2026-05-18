package com.kronos.chiron.repository;

import com.kronos.chiron.entity.EtatJournalier;
import com.kronos.chiron.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EtatJournalierRepository extends JpaRepository<EtatJournalier, Long> {

    Optional<EtatJournalier> findByUtilisateurAndDate(Utilisateur utilisateur, LocalDate date);

    List<EtatJournalier> findByUtilisateurAndDateGreaterThanEqualOrderByDateDesc(Utilisateur utilisateur, LocalDate from);
}
