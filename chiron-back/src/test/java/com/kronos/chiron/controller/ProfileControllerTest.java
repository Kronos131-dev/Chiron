package com.kronos.chiron.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kronos.chiron.dto.ProfileDto;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ProfileController.class)
class ProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ProfileService profileService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

    private ProfileDto buildProfile(String username) {
        return ProfileDto.builder()
                .username(username)
                .rank("Citoyen")
                .isPublic(true)
                .build();
    }

    @Test
    @WithMockUser(username = "alice")
    void getProfile_existingUser_returns200() throws Exception {
        when(profileService.getProfile("alice", "alice")).thenReturn(buildProfile("alice"));

        mockMvc.perform(get("/api/profile/alice").param("requestUsername", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.rank").value("Citoyen"));
    }

    @Test
    @WithMockUser(username = "alice")
    void getProfile_noRequestUsername_usesPathVariable() throws Exception {
        when(profileService.getProfile("alice", "alice")).thenReturn(buildProfile("alice"));

        mockMvc.perform(get("/api/profile/alice"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice")
    void getProfile_serviceThrowsException_returns404() throws Exception {
        when(profileService.getProfile(any(), any())).thenThrow(new RuntimeException("not found"));

        mockMvc.perform(get("/api/profile/ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "bob")
    void searchProfiles_returnsMatchingProfiles() throws Exception {
        when(profileService.searchProfiles("ali", "bob")).thenReturn(List.of(buildProfile("alice")));

        mockMvc.perform(get("/api/profile/search")
                        .param("query", "ali")
                        .param("requestUsername", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    @WithMockUser(username = "alice")
    void updateVisibility_authenticatedAsOwner_returns200() throws Exception {
        doNothing().when(profileService).updateVisibility("alice", true);

        mockMvc.perform(put("/api/profile/alice/visibility")
                        .param("isPublic", "true")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "bob")
    void updateVisibility_differentUser_returns403() throws Exception {
        mockMvc.perform(put("/api/profile/alice/visibility")
                        .param("isPublic", "true")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateVisibility_noAuthentication_returns401() throws Exception {
        mockMvc.perform(put("/api/profile/alice/visibility")
                        .param("isPublic", "true")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    void addCoach_authenticatedAsOwner_returns200() throws Exception {
        doNothing().when(profileService).addCoach("alice", "bob");

        mockMvc.perform(post("/api/profile/alice/coach/bob").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "charlie")
    void addCoach_differentUser_returns403() throws Exception {
        mockMvc.perform(post("/api/profile/alice/coach/bob").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    void addCoach_serviceThrows_returns400() throws Exception {
        doThrow(new RuntimeException("Coach not found")).when(profileService).addCoach("alice", "nobody");

        mockMvc.perform(post("/api/profile/alice/coach/nobody").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice")
    void removeCoach_authenticatedAsOwner_returns200() throws Exception {
        doNothing().when(profileService).removeCoach("alice", "bob");

        mockMvc.perform(delete("/api/profile/alice/coach/bob").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "charlie")
    void removeCoach_differentUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/profile/alice/coach/bob").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    void deleteProfile_authenticatedUser_returns200() throws Exception {
        doNothing().when(profileService).deleteProfile("alice", "alice");

        mockMvc.perform(delete("/api/profile/alice").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteProfile_notAuthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/profile/alice").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "bob")
    void deleteProfile_serviceThrowsAccessDenied_returns400() throws Exception {
        doThrow(new RuntimeException("Access denied")).when(profileService).deleteProfile("alice", "bob");

        mockMvc.perform(delete("/api/profile/alice").with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
