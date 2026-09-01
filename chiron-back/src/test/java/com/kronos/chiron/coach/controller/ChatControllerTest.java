package com.kronos.chiron.coach.controller;

import tools.jackson.databind.json.JsonMapper;
import com.kronos.chiron.coach.agent.ChironAgentRouter;
import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.coach.model.AgentType;
import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.coach.service.ConversationService;
import com.kronos.chiron.coach.service.MemoryNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({JacksonAutoConfiguration.class, ChatControllerTest.PrincipalResolverConfig.class})
@WebMvcTest(value = ChatController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class ChatControllerTest {

    /**
     * Le slice exclut la configuration de sécurité, ce qui retire aussi le résolveur de
     * {@code @AuthenticationPrincipal}. ChatController lit le principal ainsi ; sans ce
     * résolveur, Spring MVC tenterait d'instancier UserDetails comme un attribut de modèle.
     */
    @TestConfiguration
    static class PrincipalResolverConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ChironAgentRouter chironAgentRouter;
    @MockitoBean
    private UtilisateurRepository utilisateurRepository;
    @MockitoBean
    private MemoryNoteService memoryNoteService;
    @MockitoBean
    private ConversationService conversationService;
    @MockitoBean
    private ConversationMemoryManager memoryManager;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

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
        when(conversationService.getOrCreate(any(), any(), eq(AgentType.CHIRON)))
                .thenReturn(Conversation.builder().id(42L).agent(AgentType.CHIRON).build());
        when(memoryNoteService.formatForPrompt(any(), anyInt())).thenReturn("");
    }

    @Test
    @WithMockUser(username = "alice")
    void chat_validUser_returnsAgentResponse() throws Exception {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser()));
        when(chironAgentRouter.chat(eq("42"), anyString())).thenReturn("Séance enregistrée.");

        mockMvc.perform(post("/api/chat").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", "Je commence"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(42))
                .andExpect(jsonPath("$.reply").value("Séance enregistrée."));
    }

    @Test
    @WithMockUser(username = "alice")
    void chat_injectsUserContextInMessage() throws Exception {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser()));
        when(chironAgentRouter.chat(anyString(), anyString())).thenReturn("OK");

        mockMvc.perform(post("/api/chat").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", "Bonjour"))))
                .andExpect(status().isOk());

        verify(chironAgentRouter).chat(eq("42"), contains("alice"));
    }

    @Test
    @WithMockUser(username = "bob")
    void chat_usesAuthenticatedPrincipal_notABodySuppliedUsername() throws Exception {
        // Le corps ne porte plus de champ "username" : seul le principal JWT compte, pour
        // qu'un utilisateur authentifié ne puisse pas faire parler le coach au nom d'un autre.
        Utilisateur bob = Utilisateur.builder().id(2L).username("bob").role(Role.USER).build();
        when(utilisateurRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(chironAgentRouter.chat(anyString(), anyString())).thenReturn("OK");

        mockMvc.perform(post("/api/chat").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", "Bonjour"))))
                .andExpect(status().isOk());

        verify(utilisateurRepository).findByUsername("bob");
        verify(utilisateurRepository, never()).findByUsername("alice");
    }

    @Test
    @WithMockUser(username = "alice")
    void endSession_validUser_returnsAgentResponse() throws Exception {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser()));
        when(chironAgentRouter.chat(eq("42"), anyString())).thenReturn("Bien joué, soldat.");

        mockMvc.perform(post("/api/end-session").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Bien joué, soldat."));
    }
}
