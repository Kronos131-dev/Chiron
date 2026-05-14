package com.kronos.chiron.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kronos.chiron.ai.ChironAgent;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ChatController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class ChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ChironAgent chironAgent;
    @MockBean private UtilisateurRepository utilisateurRepository;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

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
