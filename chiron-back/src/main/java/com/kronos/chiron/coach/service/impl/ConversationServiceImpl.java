package com.kronos.chiron.coach.service.impl;

import com.kronos.chiron.coach.service.ConversationService;

import java.time.Clock;

import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.coach.model.ConversationMessage;
import com.kronos.chiron.coach.model.MessageRole;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.coach.persistence.ConversationMessageRepository;
import com.kronos.chiron.coach.persistence.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int TITLE_MAX_LENGTH = 80;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;

    private final Clock clock;
    @Transactional(readOnly = true)
    @Override
    public List<Conversation> listForUser(Utilisateur user) {
        return conversationRepository.findByUtilisateurOrderByUpdatedAtDesc(user);
    }

    @Transactional
    @Override
    public Conversation getOrCreate(Utilisateur user, Long conversationId) {
        if (conversationId == null) {
            Conversation conv = Conversation.builder().utilisateur(user).build();
            return conversationRepository.save(conv);
        }
        return conversationRepository.findByIdAndUtilisateur(conversationId, user)
                .orElseThrow(() -> new NoSuchElementException("Conversation introuvable"));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ConversationMessage> getMessages(Conversation conversation) {
        return conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conversation);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ConversationMessage> getMessages(Utilisateur user, Long conversationId) {
        Conversation conv = conversationRepository.findByIdAndUtilisateur(conversationId, user)
                .orElseThrow(() -> new NoSuchElementException("Conversation introuvable"));
        return conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conv);
    }

    @Transactional
    @Override
    public void recordExchange(Conversation conversation, String userMessage, String aiReply) {
        save(conversation, MessageRole.USER, userMessage);
        save(conversation, MessageRole.AI, aiReply);
        if (conversation.getTitre() == null || conversation.getTitre().isBlank()) {
            conversation.setTitre(deriveTitle(userMessage));
        }
        conversation.setUpdatedAt(LocalDateTime.now(clock));
        conversationRepository.save(conversation);
    }

    @Transactional
    @Override
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
