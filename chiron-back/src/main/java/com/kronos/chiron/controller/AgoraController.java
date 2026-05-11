package com.kronos.chiron.controller;

import com.kronos.chiron.dto.ProfileDto;
import com.kronos.chiron.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller representing the "Agora" or public hub feature.
 * Provides endpoints to fetch the list of participants and other users in the system.
 */
@RestController
@RequestMapping("/api/agora")
@RequiredArgsConstructor
public class AgoraController {

    private final ProfileService profileService;

    /**
     * Retrieves a list of all accessible user profiles (participants) in the system.
     * Enforces visibility rules based on the requesting user's identity and role.
     *
     * @param requestUsername The username of the user making the request.
     * @return A ResponseEntity containing a list of ProfileDto representing the participants.
     */
    @GetMapping("/participants")
    public ResponseEntity<List<ProfileDto>> getAllParticipants(@RequestParam(required = false) String requestUsername) {
        String reqUser = requestUsername != null ? requestUsername : "anonymous";
        return ResponseEntity.ok(profileService.getAllProfiles(reqUser));
    }
}
