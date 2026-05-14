package com.kronos.chiron.controller;

import com.kronos.chiron.dto.ProfileDto;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AgoraController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class AgoraControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProfileService profileService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

    @Test
    void getAllParticipants_withUsername_returnsProfiles() throws Exception {
        ProfileDto profile = ProfileDto.builder().username("alice").rank("Citoyen").isPublic(true).build();
        when(profileService.getAllProfiles("alice")).thenReturn(List.of(profile));

        mockMvc.perform(get("/api/agora/participants").param("requestUsername", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void getAllParticipants_withoutUsername_usesAnonymous() throws Exception {
        when(profileService.getAllProfiles("anonymous")).thenReturn(List.of());

        mockMvc.perform(get("/api/agora/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(profileService).getAllProfiles("anonymous");
    }
}
