package com.kronos.chiron.agora.controller;

import com.kronos.chiron.utilisateur.dto.ProfileDto;
import com.kronos.chiron.utilisateur.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agora")
@RequiredArgsConstructor
public class AgoraController {

    private final ProfileService profileService;

    @GetMapping("/participants")
    public ResponseEntity<List<ProfileDto>> getAllParticipants(@RequestParam(required = false) String requestUsername) {
        String reqUser = requestUsername != null ? requestUsername : "anonymous";
        return ResponseEntity.ok(profileService.getAllProfiles(reqUser));
    }
}
