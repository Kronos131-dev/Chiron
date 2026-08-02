package com.kronos.chiron.coach.service;

import org.mockito.Spy;

import java.time.ZoneId;

import java.time.Clock;

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
import java.util.NoSuchElementException;
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
    private ConversationService conversationService;

    private Utilisateur user;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        user = new Utilisateur();
        user.setId(1L);
        conversation = Conversation.builder().id(10L).utilisateur(user).build();
    }

    private List<ConversationMessage> captureSavedMessages() {
        ArgumentCaptor<ConversationMessage> captor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(conversationMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void listForUser_delegatesToRepositoryOrderedByLastActivity() {
        when(conversationRepository.findByUtilisateurOrderByUpdatedAtDesc(user))
                .thenReturn(List.of(conversation));

        assertThat(conversationService.listForUser(user)).containsExactly(conversation);
    }

    @Test
    void getOrCreate_nullId_createsAndPersistsANewConversationForTheUser() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));

        Conversation created = conversationService.getOrCreate(user, null);

        assertThat(created.getUtilisateur()).isSameAs(user);
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void getOrCreate_existingIdOwnedByUser_returnsIt() {
        when(conversationRepository.findByIdAndUtilisateur(10L, user))
                .thenReturn(Optional.of(conversation));

        assertThat(conversationService.getOrCreate(user, 10L)).isSameAs(conversation);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getOrCreate_conversationOfAnotherUser_throwsNoSuchElement() {
        when(conversationRepository.findByIdAndUtilisateur(99L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getOrCreate(user, 99L))
                .isInstanceOf(NoSuchElementException.class);
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
        when(conversationRepository.findByIdAndUtilisateur(10L, user))
                .thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                .thenReturn(List.of());

        assertThat(conversationService.getMessages(user, 10L)).isEmpty();
    }

    @Test
    void getMessages_byId_conversationOfAnotherUser_throwsNoSuchElement() {
        when(conversationRepository.findByIdAndUtilisateur(99L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getMessages(user, 99L))
                .isInstanceOf(NoSuchElementException.class);
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
        when(conversationRepository.findByIdAndUtilisateur(10L, user))
                .thenReturn(Optional.of(conversation));

        conversationService.delete(user, 10L);

        verify(conversationRepository).delete(conversation);
    }

    @Test
    void delete_conversationOfAnotherUser_throwsAndDeletesNothing() {
        when(conversationRepository.findByIdAndUtilisateur(99L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.delete(user, 99L))
                .isInstanceOf(NoSuchElementException.class);
        verify(conversationRepository, never()).delete(any(Conversation.class));
    }
}
