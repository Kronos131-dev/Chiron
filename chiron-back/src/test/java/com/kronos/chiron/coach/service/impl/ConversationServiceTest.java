package com.kronos.chiron.coach.service.impl;

import org.springframework.web.ErrorResponseException;

import org.springframework.http.HttpStatus;

import org.mockito.Spy;

import java.time.ZoneId;

import java.time.Clock;

import com.kronos.chiron.coach.model.AgentType;
import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.coach.model.ConversationMessage;
import com.kronos.chiron.coach.model.MessageRole;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.coach.persistence.ConversationMessageRepository;
import com.kronos.chiron.coach.persistence.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Spy
    private Clock clock = Clock.system(ZoneId.of("Europe/Paris"));

    @InjectMocks
    private ConversationServiceImpl conversationService;

    private Utilisateur user;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        user = new Utilisateur();
        user.setId(1L);
        conversation = Conversation.builder().id(10L).utilisateur(user).agent(AgentType.CHIRON).build();
    }

    private List<ConversationMessage> captureSavedMessages() {
        ArgumentCaptor<ConversationMessage> captor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(conversationMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void listForUser_delegatesToRepositoryOrderedByLastActivity() {
        when(conversationRepository.findByUtilisateurAndAgentOrderByUpdatedAtDesc(user, AgentType.CHIRON))
                .thenReturn(List.of(conversation));

        assertThat(conversationService.listForUser(user, AgentType.CHIRON)).containsExactly(conversation);
    }

    @Test
    void getOrCreate_nullId_createsAndPersistsANewConversationForTheUser() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));

        Conversation created = conversationService.getOrCreate(user, null, AgentType.CHIRON);

        assertThat(created.getUtilisateur()).isSameAs(user);
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void getOrCreate_existingIdOwnedByUser_returnsIt() {
        when(conversationRepository.findByIdAndUtilisateurAndAgent(10L, user, AgentType.CHIRON))
                .thenReturn(Optional.of(conversation));

        assertThat(conversationService.getOrCreate(user, 10L, AgentType.CHIRON)).isSameAs(conversation);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getOrCreate_conversationOfAnotherUser_throwsNoSuchElement() {
        when(conversationRepository.findByIdAndUtilisateurAndAgent(99L, user, AgentType.CHIRON))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getOrCreate(user, 99L, AgentType.CHIRON))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getMessages_byConversation_returnsThemChronologically() {
        ConversationMessage message = ConversationMessage.builder().content("salut").build();
        when(conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                .thenReturn(List.of(message));

        assertThat(conversationService.getMessages(conversation)).containsExactly(message);
    }

    @Test
    void getMessages_byId_checksOwnershipFirst() {
        when(conversationRepository.findByIdAndUtilisateurAndAgent(10L, user, AgentType.CHIRON))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                .thenReturn(List.of());

        assertThat(conversationService.getMessages(user, 10L, AgentType.CHIRON)).isEmpty();
    }

    @Test
    void getMessages_byId_conversationOfAnotherUser_throwsNoSuchElement() {
        when(conversationRepository.findByIdAndUtilisateurAndAgent(99L, user, AgentType.CHIRON))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getMessages(user, 99L, AgentType.CHIRON))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void recordExchange_persistsUserMessageThenAiReply() {
        conversationService.recordExchange(conversation, "combien de séries ?", "quatre");

        List<ConversationMessage> saved = captureSavedMessages();
        assertThat(saved.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(saved.get(0).getContent()).isEqualTo("combien de séries ?");
        assertThat(saved.get(1).getRole()).isEqualTo(MessageRole.AI);
        assertThat(saved.get(1).getContent()).isEqualTo("quatre");
    }

    @Test
    void recordExchange_attachesBothMessagesToTheConversation() {
        conversationService.recordExchange(conversation, "question", "réponse");

        assertThat(captureSavedMessages())
                .allSatisfy(m -> assertThat(m.getConversation()).isSameAs(conversation));
    }

    @Test
    void recordExchange_firstExchange_derivesTitleFromUserMessage() {
        conversationService.recordExchange(conversation, "Programme du jour", "ok");

        assertThat(conversation.getTitre()).isEqualTo("Programme du jour");
    }

    @Test
    void recordExchange_existingTitle_isNotOverwritten() {
        conversation.setTitre("Titre existant");

        conversationService.recordExchange(conversation, "autre chose", "ok");

        assertThat(conversation.getTitre()).isEqualTo("Titre existant");
    }

    @Test
    void recordExchange_blankTitle_isReplaced() {
        conversation.setTitre("   ");

        conversationService.recordExchange(conversation, "Nouveau sujet", "ok");

        assertThat(conversation.getTitre()).isEqualTo("Nouveau sujet");
    }

    @Test
    void recordExchange_multilineMessage_titleIsCollapsedToOneLine() {
        conversationService.recordExchange(conversation, "  Séance   push\n\ndemain  ", "ok");

        assertThat(conversation.getTitre()).isEqualTo("Séance push demain");
    }

    @Test
    void recordExchange_longMessage_titleIsTruncatedWithEllipsis() {
        String longMessage = "a".repeat(200);

        conversationService.recordExchange(conversation, longMessage, "ok");

        assertThat(conversation.getTitre()).hasSize(80);
        assertThat(conversation.getTitre()).endsWith("…");
    }

    @Test
    void recordExchange_messageExactlyAtTheLimit_isNotTruncated() {
        String exact = "a".repeat(80);

        conversationService.recordExchange(conversation, exact, "ok");

        assertThat(conversation.getTitre()).isEqualTo(exact);
        assertThat(conversation.getTitre()).doesNotEndWith("…");
    }

    @Test
    void recordExchange_touchesUpdatedAtAndSavesTheConversation() {
        conversationService.recordExchange(conversation, "question", "réponse");

        assertThat(conversation.getUpdatedAt()).isNotNull();
        verify(conversationRepository).save(conversation);
    }

    @Test
    void delete_conversationOwnedByUser_isDeleted() {
        when(conversationRepository.findByIdAndUtilisateurAndAgent(10L, user, AgentType.CHIRON))
                .thenReturn(Optional.of(conversation));

        conversationService.delete(user, 10L, AgentType.CHIRON);

        verify(conversationRepository).delete(conversation);
    }

    @Test
    void delete_conversationOfAnotherUser_throwsAndDeletesNothing() {
        when(conversationRepository.findByIdAndUtilisateurAndAgent(99L, user, AgentType.CHIRON))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.delete(user, 99L, AgentType.CHIRON))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(conversationRepository, never()).delete(any(Conversation.class));
    }
}
