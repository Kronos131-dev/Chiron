package com.kronos.chiron.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.mapper.SeanceMapper;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.service.ProgrammeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ProgrammeController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class ProgrammeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ProgrammeService programmeService;
    @MockBean private SeanceMapper seanceMapper;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

    private SeanceDto buildSeanceDto() {
        return new SeanceDto(1L, "Push Day", null, null, 1, false, null, List.of());
    }

    @Test
    void getProgrammes_returnsOk() throws Exception {
        Seance seance = new Seance();
        seance.setTitre("Push Day");

        when(programmeService.getProgrammes("alice")).thenReturn(List.of(seance));
        when(seanceMapper.toDto(seance)).thenReturn(buildSeanceDto());

        mockMvc.perform(get("/api/programmes").param("username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titre").value("Push Day"));
    }

    @Test
    void getProgrammes_emptyList_returnsEmptyArray() throws Exception {
        when(programmeService.getProgrammes("alice")).thenReturn(List.of());

        mockMvc.perform(get("/api/programmes").param("username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void creerProgramme_success_returns200() throws Exception {
        Seance saved = new Seance();
        saved.setId(42L);
        when(programmeService.sauvegarderProgramme(eq("alice"), any())).thenReturn(saved);

        mockMvc.perform(post("/api/programmes")
                        .param("username", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildSeanceDto())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("42")));
    }

    @Test
    void creerProgramme_serviceThrows_returns400() throws Exception {
        when(programmeService.sauvegarderProgramme(any(), any()))
                .thenThrow(new RuntimeException("Save failed"));

        mockMvc.perform(post("/api/programmes")
                        .param("username", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildSeanceDto())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProgrammeById_returnsOk() throws Exception {
        Seance seance = new Seance();
        seance.setId(1L);
        when(programmeService.getProgrammeById(1L, "alice")).thenReturn(seance);
        when(seanceMapper.toDto(seance)).thenReturn(buildSeanceDto());

        mockMvc.perform(get("/api/programmes/1").param("username", "alice"))
                .andExpect(status().isOk());
    }

    @Test
    void getProgrammeById_serviceThrows_returns400() throws Exception {
        when(programmeService.getProgrammeById(1L, "alice"))
                .thenThrow(new RuntimeException("Not found"));

        mockMvc.perform(get("/api/programmes/1").param("username", "alice"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void copyProgramme_success_returns200() throws Exception {
        Seance copied = new Seance();
        copied.setId(99L);
        when(programmeService.copyProgramme(1L, "bob")).thenReturn(copied);

        mockMvc.perform(post("/api/programmes/1/copy").param("targetUsername", "bob"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("99")));
    }

    @Test
    void copyProgramme_serviceThrows_returns400() throws Exception {
        when(programmeService.copyProgramme(1L, "bob"))
                .thenThrow(new RuntimeException("Private profile"));

        mockMvc.perform(post("/api/programmes/1/copy").param("targetUsername", "bob"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteProgramme_success_returns200() throws Exception {
        doNothing().when(programmeService).deleteProgramme(1L, "alice");

        mockMvc.perform(delete("/api/programmes/1").param("username", "alice"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteProgramme_serviceThrows_returns400() throws Exception {
        doThrow(new RuntimeException("Access denied")).when(programmeService).deleteProgramme(1L, "alice");

        mockMvc.perform(delete("/api/programmes/1").param("username", "alice"))
                .andExpect(status().isBadRequest());
    }
}
