package com.kronos.chiron.coach.persistence;

import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUtilisateurOrderByUpdatedAtDesc(Utilisateur utilisateur);

    Optional<Conversation> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);
}
