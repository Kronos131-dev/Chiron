package com.kronos.chiron.controller;

import com.kronos.chiron.dto.ProfileDto;
import com.kronos.chiron.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * REST controller for managing user profiles.
 * Provides endpoints for retrieving, searching, and updating profile information,
 * including visibility, avatar icons, and coaching relationships.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Retrieves a user's profile by their username.
     *
     * @param username        The username of the profile to fetch.
     * @param requestUsername The username of the user making the request, for privacy checks.
     * @return A ResponseEntity containing the ProfileDto or a not-found status.
     */
    @GetMapping("/{username}")
    public ResponseEntity<ProfileDto> getProfile(@PathVariable String username, @RequestParam(required = false) String requestUsername) {
        try {
            String reqUser = requestUsername != null ? requestUsername : username;
            return ResponseEntity.ok(profileService.getProfile(username, reqUser));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Searches for user profiles based on a query string.
     *
     * @param query           The search query.
     * @param requestUsername The username of the user making the request.
     * @return A ResponseEntity containing a list of matching profiles.
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProfileDto>> searchProfiles(@RequestParam String query, @RequestParam String requestUsername) {
        return ResponseEntity.ok(profileService.searchProfiles(query, requestUsername));
    }

    /**
     * Updates the public visibility of a user's profile.
     *
     * @param username       The username of the profile to update.
     * @param isPublic       The new visibility status.
     * @param authentication The security context of the authenticated user.
     * @return A ResponseEntity indicating success or an authorization failure.
     */
    @PutMapping("/{username}/visibility")
    public ResponseEntity<?> updateVisibility(@PathVariable String username, @RequestParam boolean isPublic, Authentication authentication) {
        if (authentication == null || !authentication.getName().equalsIgnoreCase(username)) {
             return ResponseEntity.status(403).body("Unauthorized");
        }
        profileService.updateVisibility(username, isPublic);
        return ResponseEntity.ok().build();
    }

    /**
     * Updates a user's profile icon.
     *
     * @param username       The username of the profile to update.
     * @param file           The new avatar image file.
     * @param authentication The security context of the authenticated user.
     * @return A ResponseEntity with the new filename or an error status.
     */
    @PostMapping("/{username}/icon")
    public ResponseEntity<?> updateIcon(@PathVariable String username, @RequestParam("file") MultipartFile file, Authentication authentication) {
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

    /**
     * Adds a coach to a user's profile.
     *
     * @param username       The user who is adding a coach.
     * @param coachUsername  The username of the user to be designated as coach.
     * @param authentication The security context of the authenticated user.
     * @return A ResponseEntity indicating success or an authorization failure.
     */
    @PostMapping("/{username}/coach/{coachUsername}")
    public ResponseEntity<?> addCoach(@PathVariable String username, @PathVariable String coachUsername, Authentication authentication) {
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

    /**
     * Removes a coach from a user's profile.
     *
     * @param username       The user who is removing a coach.
     * @param coachUsername  The username of the coach to be removed.
     * @param authentication The security context of the authenticated user.
     * @return A ResponseEntity indicating success or an authorization failure.
     */
    @DeleteMapping("/{username}/coach/{coachUsername}")
    public ResponseEntity<?> removeCoach(@PathVariable String username, @PathVariable String coachUsername, Authentication authentication) {
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

    /**
     * Deletes a user's profile.
     *
     * @param username       The username of the profile to delete.
     * @param authentication The security context of the authenticated user.
     * @return A ResponseEntity indicating success or an authorization failure.
     */
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
