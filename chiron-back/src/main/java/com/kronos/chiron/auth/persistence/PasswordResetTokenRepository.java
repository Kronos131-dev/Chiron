package com.kronos.chiron.auth.persistence;

import com.kronos.chiron.auth.model.PasswordResetToken;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUtilisateurAndUsedFalse(Utilisateur utilisateur);
}
