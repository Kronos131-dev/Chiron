package com.kronos.chiron.course.controller;

import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.course.dto.CourseTraceDto;
import com.kronos.chiron.course.dto.CourseTraceRequestDto;
import com.kronos.chiron.course.service.CourseTraceService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final AuthenticatedUserService authenticatedUserService;
    private final CourseTraceService courseTraceService;

    @PostMapping("/traces")
    public ResponseEntity<CourseTraceDto> enregistrerTrace(@RequestBody CourseTraceRequestDto requete) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(courseTraceService.enregistrer(user, requete));
    }

    @GetMapping("/traces/{id}")
    public ResponseEntity<CourseTraceDto> lireTrace(@PathVariable Long id) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(courseTraceService.lire(user, id));
    }
}
