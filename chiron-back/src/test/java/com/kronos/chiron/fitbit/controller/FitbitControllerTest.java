package com.kronos.chiron.fitbit.controller;

import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.fitbit.dto.FitbitLinkStatus;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.fitbit.service.FitbitSyncService;
import com.kronos.chiron.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = FitbitController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
class FitbitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FitbitService fitbitService;
    @MockitoBean
    private FitbitSyncService fitbitSyncService;
    @MockitoBean
    private AuthenticatedUserService authenticatedUserService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void callback_liaisonAcceptee_renvoieLaPageDeSucces() throws Exception {
        // Given
        when(fitbitService.handleCallback(anyString(), anyString()))
                .thenReturn(new FitbitLinkStatus(true, false, "me", "scope", LocalDateTime.of(2026, 9, 1, 11, 41)));

        // When / Then
        mockMvc.perform(get("/api/fitbit/callback").param("code", "c").param("state", "s"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Compte Fitbit connecté")));
    }

    // Given / When / Then
    // WHY: le callback s'ouvre dans le navigateur. Une exception qui s'échappe y devient la page
    // d'erreur brute de Spring, pas du JSON — c'est ce que l'athlète a vu quand la liste des
    // scopes accordés a débordé sa colonne.
    @Test
    void callback_echecInattenduDeLEnregistrement_renvoieUnePageLisible() throws Exception {
        // Given
        when(fitbitService.handleCallback(anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("value too long for type character varying(255)"));

        // When / Then
        mockMvc.perform(get("/api/fitbit/callback").param("code", "c").param("state", "s"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Connexion impossible")));
    }
}
