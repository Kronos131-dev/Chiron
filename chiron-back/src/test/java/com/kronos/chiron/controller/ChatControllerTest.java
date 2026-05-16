package com.kronos.chiron.controller;

import tools.jackson.databind.json.JsonMapper;
import com.kronos.chiron.ai.ChironAgent;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.security.JwtService;
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

    @MockitoBean private ChironAgent chironAgent;
    @MockitoBean private UtilisateurRepository utilisateurRepository;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    private Utilisateur buildUser() {
        return Utilisateur.builder()
                .id(1L)
                .username("alice")
                .role(Role.USER)
                .build();
    }

    @Test
    void chat_validUser_returnsAgentResponse() throws Exception {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser()));
        when(chironAgent.chat(eq("1"), anyString())).thenReturn("Séance enregistrée.");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "alice", "message", "Je commence"))))
                .andExpect(status().isOk())
                .andExpect(content().string("Séance enregistrée."));
    }

    @Test
    void chat_injectsUserContextInMessage() throws Exception {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser()));
        when(chironAgent.chat(anyString(), anyString())).thenReturn("OK");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "alice", "message", "Bonjour"))))
                .andExpect(status().isOk());

        verify(chironAgent).chat(eq("1"), contains("alice"));
    }

    @Test
    void endSession_validUser_returnsAgentResponse() throws Exception {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser()));
        when(chironAgent.chat(eq("1"), anyString())).thenReturn("Bien joué, soldat.");

        mockMvc.perform(post("/api/end-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "alice", "message", ""))))
                .andExpect(status().isOk())
                .andExpect(content().string("Bien joué, soldat."));
    }
}
