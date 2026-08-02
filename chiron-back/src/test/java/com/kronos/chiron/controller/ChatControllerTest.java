package com.kronos.chiron.controller;

import tools.jackson.databind.json.JsonMapper;
import com.kronos.chiron.ai.ChironAgentRouter;
import com.kronos.chiron.ai.ConversationMemoryManager;
import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.entity.Conversation;
import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.service.AiUsageService;
import com.kronos.chiron.service.ConversationService;
import com.kronos.chiron.service.MemoryNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = ChatController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class ChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;

    @MockitoBean private ChironAgentRouter chironAgentRouter;
    @MockitoBean private UtilisateurRepository utilisateurRepository;
    @MockitoBean private MemoryNoteService memoryNoteService;
    @MockitoBean private ConversationService conversationService;
    @MockitoBean private ConversationMemoryManager memoryManager;
    @MockitoBean private AiUsageService aiUsageService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    private Utilisateur buildUser() {
        return Utilisateur.builder()
                .id(1L)
                .username("alice")
                .role(Role.USER)
                .build();
    }

    @BeforeEach
    void setUp() {
        // La conversation résolue porte l'id 42 → la mémoire IA est indexée sur "42".
        when(conversationService.getOrCreate(any(), any()))
                .thenReturn(Conversation.builder().id(42L).build());
        when(aiUsageService.resolveProvider(any())).thenReturn(AiProvider.MISTRAL);
    }

    @Test
    void chat_validUser_returnsAgentResponse() throws Exception {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser()));
        when(chironAgentRouter.chatWithFallback(any(), eq("42"), anyString())).thenReturn("Séance enregistrée.");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "alice", "message", "Je commence"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(42))
                .andExpect(jsonPath("$.reply").value("Séance enregistrée."));
    }

    @Test
    void chat_injectsUserContextInMessage() throws Exception {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser()));
        when(chironAgentRouter.chatWithFallback(any(), anyString(), anyString())).thenReturn("OK");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "alice", "message", "Bonjour"))))
                .andExpect(status().isOk());

        verify(chironAgentRouter).chatWithFallback(any(), eq("42"), contains("alice"));
    }

    @Test
    void endSession_validUser_returnsAgentResponse() throws Exception {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser()));
        when(chironAgentRouter.chatWithFallback(any(), eq("42"), anyString())).thenReturn("Bien joué, soldat.");

        mockMvc.perform(post("/api/end-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "alice", "message", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Bien joué, soldat."));
    }
}
