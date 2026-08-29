package com.kronos.chiron.course.controller;

import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.course.dto.CoursePointDto;
import com.kronos.chiron.course.dto.CourseSplitDto;
import com.kronos.chiron.course.dto.CourseTraceDto;
import com.kronos.chiron.course.dto.CourseTraceRequestDto;
import com.kronos.chiron.course.service.CourseTraceService;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = CourseController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AuthenticatedUserService authenticatedUserService;
    @MockitoBean
    private CourseTraceService courseTraceService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private final Utilisateur user = Utilisateur.builder().id(1L).username("athlete").build();

    private CourseTraceDto traceEnregistree() {
        return new CourseTraceDto(42L, 5000.0, 1500, 12.0, 30.0, 5000.0, 1500,
                List.of(new CourseSplitDto(1, 300, 12.0)),
                List.of(new CoursePointDto(48.8566, 2.3522, 1_700_000_000_000L, 35.0)));
    }

    @Test
    void enregistrerTrace_traceValide_repond201AvecLesMesures() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(courseTraceService.enregistrer(eq(user), any(CourseTraceRequestDto.class)))
                .thenReturn(traceEnregistree());

        CourseTraceRequestDto requete = new CourseTraceRequestDto(List.of(
                new CoursePointDto(48.8566, 2.3522, 1_700_000_000_000L, 35.0),
                new CoursePointDto(48.8576, 2.3522, 1_700_000_300_000L, 36.0)), null);

        mockMvc.perform(post("/api/courses/traces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requete)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.distanceM").value(5000.0))
                .andExpect(jsonPath("$.dureeS").value(1500))
                .andExpect(jsonPath("$.splits[0].kilometre").value(1));
    }

    @Test
    void lireTrace_traceExistante_repond200AvecLesPoints() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(courseTraceService.lire(user, 42L)).thenReturn(traceEnregistree());

        mockMvc.perform(get("/api/courses/traces/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].lat").value(48.8566))
                .andExpect(jsonPath("$.denivelePositifM").value(30.0));
    }

    @Test
    void lireTrace_traceInconnue_repond404() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(courseTraceService.lire(user, 99L)).thenThrow(notFound("trace de course", 99L));

        mockMvc.perform(get("/api/courses/traces/99"))
                .andExpect(status().isNotFound());
    }
}
