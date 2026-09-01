package com.kronos.chiron.utilisateur.controller;

import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.utilisateur.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

}
