package com.kronos.chiron.controller;

import tools.jackson.databind.json.JsonMapper;
import com.kronos.chiron.dto.ExerciceDefinitionDto;
import com.kronos.chiron.entity.MuscleGroup;
import com.kronos.chiron.entity.NiveauDifficulte;
import com.kronos.chiron.entity.TypeEquipement;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.service.ExerciceDefinitionService;
import com.kronos.chiron.util.ExerciceDataImporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = ExerciceDefinitionController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class ExerciceDefinitionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;

    @MockitoBean private ExerciceDefinitionService service;
    @MockitoBean private ExerciceDataImporter importer;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    private ExerciceDefinitionDto buildDto(Long id, String nomFr, String nomEn) {
        return new ExerciceDefinitionDto(
                id, nomFr, nomEn,
                "/api/exercices/" + id + "/image/0",
                "/api/exercices/" + id + "/image/1",
                MuscleGroup.PECTORAUX,
                List.of(),
                TypeEquipement.BARRE,
                NiveauDifficulte.INTERMEDIAIRE,
                null, null
        );
    }

    // ── GET /api/exercices ────────────────────────────────────────────────────

    @Test
    void search_noParams_returnsOk() throws Exception {
        when(service.search(null, null, null, null)).thenReturn(List.of(buildDto(1L, "Développé couché", "Bench Press")));

        mockMvc.perform(get("/api/exercices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nomFr").value("Développé couché"))
                .andExpect(jsonPath("$[0].nomEn").value("Bench Press"));
    }

    @Test
    void search_withQueryParam_passesItToService() throws Exception {
        when(service.search("squat", null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/exercices").param("q", "squat"))
                .andExpect(status().isOk());

        verify(service).search("squat", null, null, null);
    }

    @Test
    void search_withAllFilters_passesThemToService() throws Exception {
        when(service.search("bench", "PECTORAUX", "BARRE", "INTERMEDIAIRE")).thenReturn(List.of());

        mockMvc.perform(get("/api/exercices")
                        .param("q", "bench")
                        .param("muscle", "PECTORAUX")
                        .param("equipement", "BARRE")
                        .param("difficulte", "INTERMEDIAIRE"))
                .andExpect(status().isOk());

        verify(service).search("bench", "PECTORAUX", "BARRE", "INTERMEDIAIRE");
    }

    @Test
    void search_emptyResults_returnsEmptyArray() throws Exception {
        when(service.search(any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/exercices").param("q", "xyzzyx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/exercices/{id} ───────────────────────────────────────────────

    @Test
    void getById_found_returnsOk() throws Exception {
        when(service.getById(42L)).thenReturn(buildDto(42L, "Squat barre", "Barbell Squat"));

        mockMvc.perform(get("/api/exercices/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.nomFr").value("Squat barre"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(service.getById(999L)).thenThrow(new NoSuchElementException("not found"));

        mockMvc.perform(get("/api/exercices/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/exercices/{id}/image/{index} ─────────────────────────────────

    @Test
    void streamImage_validRequest_returnsJpeg() throws Exception {
        Resource img = new ByteArrayResource(new byte[]{1, 2, 3});
        when(service.streamImage(1L, 0)).thenReturn(img);

        mockMvc.perform(get("/api/exercices/1/image/0"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void streamImage_imageNotFound_returns404() throws Exception {
        when(service.streamImage(1L, 0)).thenThrow(new NoSuchElementException("no image"));

        mockMvc.perform(get("/api/exercices/1/image/0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamImage_invalidIndex_returns400() throws Exception {
        when(service.streamImage(1L, 2)).thenThrow(new IllegalArgumentException("bad index"));

        mockMvc.perform(get("/api/exercices/1/image/2"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/exercices/import ────────────────────────────────────────────

    @Test
    void importDataset_validFile_returnsImportCount() throws Exception {
        when(importer.importFromFile(any(), any())).thenReturn(873);

        MockMultipartFile file = new MockMultipartFile(
                "file", "exercises.json", "application/json", "[]".getBytes());

        mockMvc.perform(multipart("/api/exercices/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(873));
    }

    @Test
    void importDataset_withImageDir_passesItToImporter() throws Exception {
        when(importer.importFromFile(any(), any())).thenReturn(0);

        MockMultipartFile file = new MockMultipartFile(
                "file", "exercises.json", "application/json", "[]".getBytes());

        mockMvc.perform(multipart("/api/exercices/import")
                        .file(file)
                        .param("imageDir", "/tmp/images"))
                .andExpect(status().isOk());

        verify(importer).importFromFile(any(), eq(java.nio.file.Path.of("/tmp/images")));
    }
}
