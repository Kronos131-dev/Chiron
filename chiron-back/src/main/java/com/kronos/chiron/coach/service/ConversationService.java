package com.kronos.chiron.coach.service;

import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.coach.model.ConversationMessage;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import java.util.List;

public interface ConversationService {

    List<Conversation> listForUser(Utilisateur user);

    Conversation getOrCreate(Utilisateur user, Long conversationId);

    List<ConversationMessage> getMessages(Conversation conversation);

    List<ConversationMessage> getMessages(Utilisateur user, Long conversationId);

    void recordExchange(Conversation conversation, String userMessage, String aiReply);

    void delete(Utilisateur user, Long conversationId);
}
