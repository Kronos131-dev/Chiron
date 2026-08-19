package com.kronos.chiron.sante.persistence;

import com.kronos.chiron.sante.model.SanteSyncState;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SanteSyncStateRepository extends JpaRepository<SanteSyncState, Long> {

    Optional<SanteSyncState> findByUtilisateurAndTypeDonnee(Utilisateur utilisateur, String typeDonnee);

    List<SanteSyncState> findByUtilisateur(Utilisateur utilisateur);
}
