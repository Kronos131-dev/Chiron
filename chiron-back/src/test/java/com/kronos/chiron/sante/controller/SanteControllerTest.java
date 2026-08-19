package com.kronos.chiron.sante.controller;

import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.sante.dto.SanteActiviteDto;
import com.kronos.chiron.sante.dto.SanteFcPointDto;
import com.kronos.chiron.sante.dto.SanteJourDto;
import com.kronos.chiron.sante.dto.SanteResumeDto;
import com.kronos.chiron.sante.dto.SanteSyncEtatDto;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.sante.service.SanteDiagnosticService;
import com.kronos.chiron.sante.service.SanteQueryService;
import com.kronos.chiron.sante.service.SanteSyncService;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = SanteController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
class SanteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SanteQueryService santeQueryService;
    @MockitoBean
    private SanteSyncService santeSyncService;
    @MockitoBean
    private SanteDiagnosticService santeDiagnosticService;
    @MockitoBean
    private AuthenticatedUserService authenticatedUserService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private final Utilisateur user = Utilisateur.builder().id(1L).username("athlete").build();

    @Test
    void resume_returnsResumeDto() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(santeQueryService.getResume(user)).thenReturn(
                new SanteResumeDto(true, false, LocalDate.of(2026, 8, 17), 8500, 6.2, 2100, 450, 32, 58, 82, 310.0,
                        74));

        mockMvc.perform(get("/api/sante/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.pas").value(8500))
                .andExpect(jsonPath("$.scoreSommeil").value(82));
    }

    @Test
    void jours_defaultsTo30Days() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(santeQueryService.getJours(user, 30)).thenReturn(List.of());

        mockMvc.perform(get("/api/sante/jours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(santeQueryService).getJours(user, 30);
    }

    @Test
    void jours_explicitDaysParam_isForwarded() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(santeQueryService.getJours(user, 90)).thenReturn(
                List.of(new SanteJourDto(LocalDate.of(2026, 8, 17), 9000, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null)));

        mockMvc.perform(get("/api/sante/jours").param("jours", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pas").value(9000));
    }

    @Test
    void frequenceCardiaqueJour_requiresDateParam() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);

        mockMvc.perform(get("/api/sante/frequence-cardiaque"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void frequenceCardiaqueJour_withDate_returnsPoints() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(santeQueryService.getFrequenceCardiaqueJour(user, LocalDate.of(2026, 8, 17))).thenReturn(
                List.of(new SanteFcPointDto(LocalDateTime.of(2026, 8, 17, 6, 0), 55, 62.0, 70)));

        mockMvc.perform(get("/api/sante/frequence-cardiaque").param("date", "2026-08-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fcMoyenne").value(62.0));
    }

    @Test
    void activites_defaultsTo366Days_noSourceFilter() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(santeQueryService.getActivites(user, 366, null)).thenReturn(List.of(
                new SanteActiviteDto(1L, SourceActivite.CHIRON_MUSCU, TypeActivite.MUSCULATION,
                        LocalDateTime.of(2026, 8, 17, 18, 0), LocalDateTime.of(2026, 8, 17, 19, 15), 406, 124.0,
                        95, 168, 14, 46, 15, 0, 73, 71.0, 42L, false)));

        mockMvc.perform(get("/api/sante/activites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].typeActivite").value("MUSCULATION"))
                .andExpect(jsonPath("$[0].seanceId").value(42));

        verify(santeQueryService).getActivites(user, 366, null);
    }

    @Test
    void activites_sourceParam_isForwarded() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(santeQueryService.getActivites(user, 30, SourceActivite.CHIRON_MUSCU)).thenReturn(List.of());

        mockMvc.perform(get("/api/sante/activites").param("jours", "30").param("source", "CHIRON_MUSCU"))
                .andExpect(status().isOk());

        verify(santeQueryService).getActivites(user, 30, SourceActivite.CHIRON_MUSCU);
    }

    @Test
    void forcerSync_triggersBackfillAndRecentSync() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(santeQueryService.getSyncEtat(user)).thenReturn(List.of());

        mockMvc.perform(post("/api/sante/sync"))
                .andExpect(status().isOk());

        verify(santeSyncService).ensureBackfillAsync("athlete");
        verify(santeSyncService).syncRecent("athlete", 7);
    }

    @Test
    void syncEtat_returnsStateList() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(santeQueryService.getSyncEtat(user)).thenReturn(
                List.of(new SanteSyncEtatDto("STEPS", LocalDate.of(2026, 8, 17), LocalDateTime.now(), "OK", null)));

        mockMvc.perform(get("/api/sante/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].typeDonnee").value("STEPS"))
                .andExpect(jsonPath("$[0].statut").value("OK"));
    }
}
