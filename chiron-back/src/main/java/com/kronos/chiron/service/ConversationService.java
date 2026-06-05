package com.kronos.chiron.service;

import com.kronos.chiron.entity.Conversation;
import com.kronos.chiron.entity.ConversationMessage;
import com.kronos.chiron.entity.MessageRole;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.ConversationMessageRepository;
import com.kronos.chiron.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Gère les conversations persistées du coach Chiron : création, listing, messages, suppression.
 * Toute la frontière transactionnelle vit ici (jamais dans les contrôleurs).
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int TITLE_MAX_LENGTH = 80;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;

    /** Conversations de l'utilisateur, de la plus récemment active à la plus ancienne. */
    @Transactional(readOnly = true)
    public List<Conversation> listForUser(Utilisateur user) {
        return conversationRepository.findByUtilisateurOrderByUpdatedAtDesc(user);
    }

    /**
     * Renvoie la conversation demandée (vérifiée comme appartenant à l'utilisateur), ou en crée
     * une nouvelle si {@code conversationId} est null.
     */
    @Transactional
    public Conversation getOrCreate(Utilisateur user, Long conversationId) {
        if (conversationId == null) {
            Conversation conv = Conversation.builder().utilisateur(user).build();
            return conversationRepository.save(conv);
        }
        return conversationRepository.findByIdAndUtilisateur(conversationId, user)
                .orElseThrow(() -> new NoSuchElementException("Conversation introuvable"));
    }

    /** Messages d'une conversation, dans l'ordre chronologique. */
    @Transactional(readOnly = true)
    public List<ConversationMessage> getMessages(Conversation conversation) {
        return conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conversation);
    }

    /** Messages d'une conversation de l'utilisateur (vérifie l'appartenance). */
    @Transactional(readOnly = true)
    public List<ConversationMessage> getMessages(Utilisateur user, Long conversationId) {
        Conversation conv = conversationRepository.findByIdAndUtilisateur(conversationId, user)
                .orElseThrow(() -> new NoSuchElementException("Conversation introuvable"));
        return conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conv);
    }

    /**
     * Persiste un échange complet (message utilisateur puis réponse IA), pose le titre à partir
     * du premier message si besoin, et touche {@code updatedAt} pour remonter la conversation.
     */
    @Transactional
    public void recordExchange(Conversation conversation, String userMessage, String aiReply) {
        save(conversation, MessageRole.USER, userMessage);
        save(conversation, MessageRole.AI, aiReply);
        if (conversation.getTitre() == null || conversation.getTitre().isBlank()) {
            conversation.setTitre(deriveTitle(userMessage));
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    /** Supprime une conversation de l'utilisateur (messages supprimés en cascade). */
    @Transactional
    public void delete(Utilisateur user, Long conversationId) {
        Conversation conv = conversationRepository.findByIdAndUtilisateur(conversationId, user)
                .orElseThrow(() -> new NoSuchElementException("Conversation introuvable"));
        conversationRepository.delete(conv);
    }

    private void save(Conversation conversation, MessageRole role, String content) {
        conversationMessageRepository.save(ConversationMessage.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .build());
    }

    private String deriveTitle(String firstMessage) {
        String oneLine = firstMessage.strip().replaceAll("\\s+", " ");
        if (oneLine.length() <= TITLE_MAX_LENGTH) {
            return oneLine;
        }
        return oneLine.substring(0, TITLE_MAX_LENGTH - 1).strip() + "…";
    }
}
