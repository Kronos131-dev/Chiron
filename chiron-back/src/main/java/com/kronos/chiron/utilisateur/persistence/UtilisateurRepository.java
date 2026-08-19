package com.kronos.chiron.utilisateur.persistence;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByUsername(String username);

    List<Utilisateur> findByUsernameContainingIgnoreCase(String username);

    Optional<Utilisateur> findByEmail(String email);

    Optional<Utilisateur> findByUsernameIgnoreCase(String username);

    List<Utilisateur> findByNomIgnoreCaseOrPrenomIgnoreCase(String nom, String prenom);

    List<Utilisateur> findByPrenomIgnoreCaseAndNomIgnoreCase(String prenom, String nom);

    List<Utilisateur> findByFitbitRefreshTokenEncryptedIsNotNull();
}
