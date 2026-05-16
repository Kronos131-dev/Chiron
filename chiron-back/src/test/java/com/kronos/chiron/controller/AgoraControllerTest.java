package com.kronos.chiron.controller;

import com.kronos.chiron.dto.ProfileDto;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = AgoraController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class AgoraControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProfileService profileService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

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
