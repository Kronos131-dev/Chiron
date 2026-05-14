package com.kronos.chiron.controller;

import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.mapper.SeanceMapper;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.repository.SeanceRepository;
import com.kronos.chiron.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = JournalController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
class JournalControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private SeanceRepository seanceRepository;
    @MockBean private SeanceMapper seanceMapper;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

    @Test
    void getHistorique_returnsListOfSessions() throws Exception {
        Seance seance = new Seance();
        seance.setId(1L);
        seance.setTitre("Push Day");
        seance.setStartTime(LocalDateTime.of(2025, 3, 1, 10, 0));

        SeanceDto dto = new SeanceDto(1L, "Push Day", LocalDateTime.of(2025, 3, 1, 10, 0),
                null, 9, true, null, List.of());

        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc("alice"))
                .thenReturn(List.of(seance));
        when(seanceMapper.toDto(seance)).thenReturn(dto);

        mockMvc.perform(get("/api/journal/historique").param("username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titre").value("Push Day"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getHistorique_noSessions_returnsEmptyArray() throws Exception {
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc("bob"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/journal/historique").param("username", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getHistorique_missingUsernameParam_returns400() throws Exception {
        mockMvc.perform(get("/api/journal/historique"))
                .andExpect(status().isBadRequest());
    }
}
