package com.kronos.chiron.sante.persistence;

import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SanteFrequenceCardiaqueRepository extends JpaRepository<SanteFrequenceCardiaque, Long> {

    Optional<SanteFrequenceCardiaque> findByUtilisateurAndHorodatage(Utilisateur utilisateur,
            LocalDateTime horodatage);

    List<SanteFrequenceCardiaque> findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(Utilisateur utilisateur,
            LocalDateTime from, LocalDateTime to);
}
