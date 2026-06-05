package com.kronos.chiron.repository;

import com.kronos.chiron.entity.Conversation;
import com.kronos.chiron.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findByConversationOrderByCreatedAtAsc(Conversation conversation);
}
