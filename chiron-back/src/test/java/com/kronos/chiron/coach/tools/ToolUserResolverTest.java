package com.kronos.chiron.coach.tools;

import com.kronos.chiron.coach.persistence.ConversationRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.ErrorResponseException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolUserResolverTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private ToolUserResolver toolUserResolver;

    @Test
    void load_conversationOwnedByUser_returnsThatUser() {
        Utilisateur owner = Utilisateur.builder().id(7L).username("athlete").build();
        when(conversationRepository.findOwnerId(42L)).thenReturn(Optional.of(7L));
        when(utilisateurRepository.findById(7L)).thenReturn(Optional.of(owner));

        Utilisateur result = toolUserResolver.load("42");

        assertThat(result).isEqualTo(owner);
    }

    @Test
    void loadId_conversationOwnedByUser_returnsOwnerId() {
        when(conversationRepository.findOwnerId(42L)).thenReturn(Optional.of(7L));

        Long ownerId = toolUserResolver.loadId("42");

        assertThat(ownerId).isEqualTo(7L);
    }

    @Test
    void load_unknownConversation_throwsNotFound() {
        when(conversationRepository.findOwnerId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> toolUserResolver.load("999"))
                .isInstanceOf(ErrorResponseException.class)
                .hasMessageContaining("999");
    }

    @Test
    void load_nonNumericMemoryId_throwsBadRequest() {
        assertThatThrownBy(() -> toolUserResolver.load("not-a-number"))
                .isInstanceOf(ErrorResponseException.class)
                .hasMessageContaining("not-a-number");
    }
}
