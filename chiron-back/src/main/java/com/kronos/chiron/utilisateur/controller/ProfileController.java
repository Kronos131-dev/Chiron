package com.kronos.chiron.utilisateur.controller;

import com.kronos.chiron.utilisateur.dto.ProfileDto;
import com.kronos.chiron.utilisateur.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/{username}")
    public ResponseEntity<ProfileDto> getProfile(@PathVariable String username,
            @RequestParam(required = false) String requestUsername) {
        try {
            String reqUser = requestUsername != null ? requestUsername : username;
            return ResponseEntity.ok(profileService.getProfile(username, reqUser));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProfileDto>> searchProfiles(@RequestParam String query,
            @RequestParam String requestUsername) {
        return ResponseEntity.ok(profileService.searchProfiles(query, requestUsername));
    }

    @PutMapping("/{username}/visibility")
    public ResponseEntity<?> updateVisibility(@PathVariable String username, @RequestParam boolean isPublic,
            Authentication authentication) {
        if (authentication == null || !authentication.getName().equalsIgnoreCase(username)) {
            return ResponseEntity.status(403).body("Unauthorized");
        }
        profileService.updateVisibility(username, isPublic);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{username}/icon")
    public ResponseEntity<?> updateIcon(@PathVariable String username, @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.getName().equalsIgnoreCase(username)) {
                return ResponseEntity.status(403).body("Unauthorized");
            }

            String fileName = profileService.updateIcon(username, file);
            return ResponseEntity.ok().body("{\"fileName\":\"" + fileName + "\"}");
        } catch (Exception e) {
            System.err.println("UPLOAD ERROR: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{username}/coach/{coachUsername}")
    public ResponseEntity<?> addCoach(@PathVariable String username, @PathVariable String coachUsername,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.getName().equalsIgnoreCase(username)) {
                return ResponseEntity.status(403).body("Unauthorized");
            }
            profileService.addCoach(username, coachUsername);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{username}/coach/{coachUsername}")
    public ResponseEntity<?> removeCoach(@PathVariable String username, @PathVariable String coachUsername,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.getName().equalsIgnoreCase(username)) {
                return ResponseEntity.status(403).body("Unauthorized");
            }
            profileService.removeCoach(username, coachUsername);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<?> deleteProfile(@PathVariable String username, Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(403).body("Unauthorized");
            }
            String requestUsername = authentication.getName();
            profileService.deleteProfile(username, requestUsername);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
