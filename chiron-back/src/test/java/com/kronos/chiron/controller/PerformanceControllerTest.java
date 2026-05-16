package com.kronos.chiron.controller;

import tools.jackson.databind.json.JsonMapper;
import com.kronos.chiron.dto.ExercisePerformanceDto;
import com.kronos.chiron.dto.PerformanceRecordDto;
import com.kronos.chiron.dto.PerformanceSummaryDto;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.service.PerformanceService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = PerformanceController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
class PerformanceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;

    @MockitoBean private PerformanceService performanceService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    private PerformanceSummaryDto buildSummary() {
        return PerformanceSummaryDto.builder()
                .overallTier("Éphèbe").overallTierLevel(1)
                .exercises(List.of()).build();
    }

    @Test
    void getSummary_returnsSummaryDto() throws Exception {
        when(performanceService.getSummary("alice")).thenReturn(buildSummary());

        mockMvc.perform(get("/api/performance/alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallTier").value("Éphèbe"))
                .andExpect(jsonPath("$.overallTierLevel").value(1));
    }

    @Test
    void addRecord_validData_returns200() throws Exception {
        when(performanceService.addRecord(eq("alice"), any())).thenReturn(buildSummary());
        PerformanceRecordDto dto = new PerformanceRecordDto("DEVELOPPE_COUCHE", 100.0, 5);

        mockMvc.perform(post("/api/performance/alice/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void updateBodyweight_validWeight_returns200() throws Exception {
        when(performanceService.updateBodyweight("alice", 80.0)).thenReturn(buildSummary());

        mockMvc.perform(put("/api/performance/alice/bodyweight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("poidsCorps", 80.0))))
                .andExpect(status().isOk());
    }

    @Test
    void updateBodyweight_zeroWeight_returns400() throws Exception {
        mockMvc.perform(put("/api/performance/alice/bodyweight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("poidsCorps", 0.0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateBodyweight_negativeWeight_returns400() throws Exception {
        mockMvc.perform(put("/api/performance/alice/bodyweight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("poidsCorps", -5.0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateBodyweight_missingField_returns400() throws Exception {
        mockMvc.perform(put("/api/performance/alice/bodyweight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_returnsListOfDtos() throws Exception {
        ExercisePerformanceDto dto = ExercisePerformanceDto.builder()
                .exerciseType("SQUAT").nom("Squat").poids(120.0).nombreReps(5).build();
        when(performanceService.getHistory("alice", "SQUAT")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/performance/alice/history/SQUAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exerciseType").value("SQUAT"))
                .andExpect(jsonPath("$[0].poids").value(120.0));
    }

    @Test
    void getHistory_emptyList_returnsEmptyArray() throws Exception {
        when(performanceService.getHistory("bob", "SQUAT")).thenReturn(List.of());

        mockMvc.perform(get("/api/performance/bob/history/SQUAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
