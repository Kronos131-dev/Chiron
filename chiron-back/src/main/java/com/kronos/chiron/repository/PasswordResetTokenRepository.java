package com.kronos.chiron.repository;

import com.kronos.chiron.entity.PasswordResetToken;
import com.kronos.chiron.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUtilisateurAndUsedFalse(Utilisateur utilisateur);
}
