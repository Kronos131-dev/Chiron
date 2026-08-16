package com.kronos.chiron.utilisateur.controller;

import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.utilisateur.dto.AiProviderDto;
import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.utilisateur.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JacksonAutoConfiguration.class, SettingsControllerTest.PrincipalResolverConfig.class})
@WebMvcTest(value = SettingsController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class SettingsControllerTest {

    /**
     * Le slice exclut la configuration de sécurité, ce qui retire aussi le résolveur de
     * {@code @AuthenticationPrincipal}. SettingsController lit le principal ainsi ; sans ce
     * résolveur, Spring MVC tenterait d'instancier UserDetails comme un attribut de modèle.
     */
    @TestConfiguration
    static class PrincipalResolverConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettingsService settingsService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "athlete")
    void updateAiProvider_bodySentByTheFrontend_isAccepted() throws Exception {
        // Given le corps exact qu'envoie chiron-api.ts : le seul champ « provider »
        // When / Then il doit être accepté, sans exiger de champ calculé côté serveur
        mockMvc.perform(put("/api/settings/ai-provider").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"MISTRAL\"}"))
                .andExpect(status().isOk());

        verify(settingsService).updateAiProvider("athlete", AiProvider.MISTRAL);
    }

    @Test
    @WithMockUser(username = "athlete")
    void updateAiProvider_switchingToGemini_isAccepted() throws Exception {
        mockMvc.perform(put("/api/settings/ai-provider").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GEMINI\"}"))
                .andExpect(status().isOk());

        verify(settingsService).updateAiProvider("athlete", AiProvider.GEMINI);
    }

    @Test
    @WithMockUser(username = "athlete")
    void getAiProvider_returnsTheProviderAndWhetherGeminiIsAvailable() throws Exception {
        when(settingsService.getAiProvider("athlete"))
                .thenReturn(new AiProviderDto(AiProvider.GEMINI, true));

        mockMvc.perform(get("/api/settings/ai-provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GEMINI"))
                .andExpect(jsonPath("$.geminiAvailable").value(true));
    }
}
