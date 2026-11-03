package com.kronos.chiron.exportation.controller;

import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.exportation.dto.ExportProgrammeDto;
import com.kronos.chiron.exportation.dto.ExportProgrammeRequest;
import com.kronos.chiron.exportation.dto.FichierExporte;
import com.kronos.chiron.exportation.dto.ModeleExport;
import com.kronos.chiron.exportation.service.ExportProgrammeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.kronos.chiron.core.exceptions.ErrorFactory.conflict;
import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = ExportProgrammeController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
class ExportProgrammeControllerTest {

    private static final String CORPS = """
            {"nom":"Programme force","modeleIds":[5,6],"seances":[{"seanceId":30,"modeleId":6}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportProgrammeService exportProgrammeService;
    @MockitoBean
    private AuthenticatedUserService authenticatedUserService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private ExportProgrammeDto export() {
        return new ExportProgrammeDto(9L, "Programme force", LocalDateTime.of(2026, 11, 2, 10, 0),
                List.of(new ModeleExport(5L, "Push A")), List.of(), List.of(), 4);
    }

    @BeforeEach
    void setUp() {
        when(authenticatedUserService.getAuthenticatedUsername()).thenReturn("alice");
    }

    @Test
    void lister_rendLesExportsDeLUtilisateurAuthentifie() throws Exception {
        when(exportProgrammeService.lister("alice")).thenReturn(List.of(export()));

        mockMvc.perform(get("/api/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("Programme force"))
                .andExpect(jsonPath("$[0].modeles[0].titre").value("Push A"))
                .andExpect(jsonPath("$[0].nbSeances").value(4));
    }

    @Test
    void creer_repond201AvecLExportCree() throws Exception {
        when(exportProgrammeService.creer(eq("alice"), any(ExportProgrammeRequest.class))).thenReturn(export());

        mockMvc.perform(post("/api/exports").contentType(MediaType.APPLICATION_JSON).content(CORPS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.nbSeances").value(4));
    }

    @Test
    void creer_transmetLaSelectionAuService() throws Exception {
        when(exportProgrammeService.creer(eq("alice"), any(ExportProgrammeRequest.class))).thenReturn(export());

        mockMvc.perform(post("/api/exports").contentType(MediaType.APPLICATION_JSON).content(CORPS));

        org.mockito.ArgumentCaptor<ExportProgrammeRequest> capture = org.mockito.ArgumentCaptor
                .forClass(ExportProgrammeRequest.class);
        verify(exportProgrammeService).creer(eq("alice"), capture.capture());
        org.assertj.core.api.Assertions.assertThat(capture.getValue().modeleIds()).containsExactly(5L, 6L);
        org.assertj.core.api.Assertions.assertThat(capture.getValue().seances().get(0).modeleId()).isEqualTo(6L);
    }

    @Test
    void creer_transmetLesSeancesExclues() throws Exception {
        when(exportProgrammeService.creer(eq("alice"), any(ExportProgrammeRequest.class))).thenReturn(export());

        mockMvc.perform(post("/api/exports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"E\",\"modeleIds\":[5],\"seancesExclues\":[40,41]}"));

        org.mockito.ArgumentCaptor<ExportProgrammeRequest> capture = org.mockito.ArgumentCaptor
                .forClass(ExportProgrammeRequest.class);
        verify(exportProgrammeService).creer(eq("alice"), capture.capture());
        org.assertj.core.api.Assertions.assertThat(capture.getValue().seancesExclues()).containsExactly(40L, 41L);
    }

    @Test
    void creer_sansLaListeDExclusions_laTransmetCommeAbsente() throws Exception {
        when(exportProgrammeService.creer(eq("alice"), any(ExportProgrammeRequest.class))).thenReturn(export());

        mockMvc.perform(post("/api/exports").contentType(MediaType.APPLICATION_JSON).content(CORPS));

        org.mockito.ArgumentCaptor<ExportProgrammeRequest> capture = org.mockito.ArgumentCaptor
                .forClass(ExportProgrammeRequest.class);
        verify(exportProgrammeService).creer(eq("alice"), capture.capture());
        org.assertj.core.api.Assertions.assertThat(capture.getValue().seancesExclues()).isNull();
    }

    @Test
    void creer_nomVide_repond400SansAppelerLeService() throws Exception {
        mockMvc.perform(post("/api/exports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"\",\"modeleIds\":[5]}"))
                .andExpect(status().isBadRequest());

        verify(exportProgrammeService, never()).creer(any(), any());
    }

    @Test
    void creer_sansSeanceDeProgramme_repond400() throws Exception {
        mockMvc.perform(post("/api/exports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"E\",\"modeleIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creer_conflit_repond409AvecLeMessage() throws Exception {
        when(exportProgrammeService.creer(any(), any())).thenThrow(conflict("Un export porte déjà ce nom"));

        mockMvc.perform(post("/api/exports").contentType(MediaType.APPLICATION_JSON).content(CORPS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Un export porte déjà ce nom"));
    }

    @Test
    void detail_unExportDUnAutre_repond404() throws Exception {
        when(exportProgrammeService.detail("alice", 9L)).thenThrow(notFound("export", 9L));

        mockMvc.perform(get("/api/exports/9")).andExpect(status().isNotFound());
    }

    @Test
    void modifier_renvoieLExportMisAJour() throws Exception {
        when(exportProgrammeService.modifier(eq("alice"), eq(9L), any(ExportProgrammeRequest.class)))
                .thenReturn(export());

        mockMvc.perform(put("/api/exports/9").contentType(MediaType.APPLICATION_JSON).content(CORPS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void telecharger_rendUnFichierXlsxJoint() throws Exception {
        when(exportProgrammeService.telecharger("alice", 9L))
                .thenReturn(new FichierExporte("Programme-force.xlsx", new byte[]{1, 2, 3}));

        mockMvc.perform(get("/api/exports/9/telecharger"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("Programme-force.xlsx")));
    }

    @Test
    void supprimer_repond204() throws Exception {
        mockMvc.perform(delete("/api/exports/9")).andExpect(status().isNoContent());

        verify(exportProgrammeService).supprimer("alice", 9L);
    }
}
