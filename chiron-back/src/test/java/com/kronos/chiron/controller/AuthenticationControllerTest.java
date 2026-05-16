package com.kronos.chiron.controller;

import tools.jackson.databind.json.JsonMapper;
import com.kronos.chiron.dto.auth.AuthenticationRequest;
import com.kronos.chiron.dto.auth.AuthenticationResponse;
import com.kronos.chiron.dto.auth.RegisterRequest;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.service.AuthenticationService;
import com.kronos.chiron.service.SettingsService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = AuthenticationController.class, excludeAutoConfiguration = {SecurityAutoConfiguration.class})
class AuthenticationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;

    @MockitoBean private AuthenticationService authenticationService;
    @MockitoBean private SettingsService settingsService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void register_validRequest_returns200WithToken() throws Exception {
        when(authenticationService.register(any())).thenReturn(new AuthenticationResponse("token-abc"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("alice", "alice@test.com", "pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-abc"));
    }

    @Test
    void authenticate_validCredentials_returns200WithToken() throws Exception {
        when(authenticationService.authenticate(any())).thenReturn(new AuthenticationResponse("token-xyz"));

        mockMvc.perform(post("/api/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthenticationRequest("alice", "pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-xyz"));
    }

    @Test
    void register_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
