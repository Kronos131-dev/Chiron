package com.kronos.chiron.coach.persistence;

import com.kronos.chiron.coach.model.AgentType;
import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUtilisateurAndAgentOrderByUpdatedAtDesc(Utilisateur utilisateur, AgentType agent);

    Optional<Conversation> findByIdAndUtilisateurAndAgent(Long id, Utilisateur utilisateur, AgentType agent);

    @Query("select c.utilisateur.id from Conversation c where c.id = :conversationId")
    Optional<Long> findOwnerId(@Param("conversationId") Long conversationId);
}
