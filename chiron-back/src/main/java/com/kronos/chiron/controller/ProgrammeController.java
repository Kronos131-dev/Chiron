package com.kronos.chiron.controller;

import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.mapper.SeanceMapper;
import com.kronos.chiron.service.ProgrammeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing workout programs (templates).
 * Provides endpoints for creating, retrieving, copying, and deleting programs.
 */
@RestController
@RequestMapping("/api/programmes")
@RequiredArgsConstructor
public class ProgrammeController {

    private final ProgrammeService programmeService;
    private final SeanceMapper seanceMapper;

    /**
     * Retrieves all workout programs belonging to a specific user.
     *
     * @param username The username of the user.
     * @return A ResponseEntity containing a list of SeanceDto representing the user's programs.
     */
    @GetMapping
    public ResponseEntity<List<SeanceDto>> getProgrammes(@RequestParam String username) {
        List<Seance> programmes = programmeService.getProgrammes(username);
        List<SeanceDto> dtos = programmes.stream().map(seanceMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Creates or updates a workout program for the specified user.
     *
     * @param username  The username of the requester.
     * @param seanceDto The DTO containing the program details.
     * @return A ResponseEntity confirming the save action.
     */
    @PostMapping
    public ResponseEntity<?> creerProgramme(@RequestParam String username, @RequestBody SeanceDto seanceDto) {
        try {
            Seance savedSeance = programmeService.sauvegarderProgramme(username, seanceDto);
            return ResponseEntity.ok("Program saved with ID: " + savedSeance.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error while saving: " + e.getMessage());
        }
    }

    /**
     * Retrieves a specific workout program by its ID.
     *
     * @param id       The ID of the program.
     * @param username The username of the requesting user (for privacy checks).
     * @return A ResponseEntity containing the requested SeanceDto.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProgrammeById(@PathVariable Long id, @RequestParam String username) {
        try {
            Seance seance = programmeService.getProgrammeById(id, username);
            return ResponseEntity.ok(seanceMapper.toDto(seance));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Copies a specific public or authorized program into the target user's profile.
     *
     * @param id             The ID of the source program.
     * @param targetUsername The username of the user receiving the copy.
     * @return A ResponseEntity confirming the copy action.
     */
    @PostMapping("/{id}/copy")
    public ResponseEntity<?> copyProgrammeToMyProfile(@PathVariable Long id, @RequestParam String targetUsername) {
        try {
            Seance copiedSeance = programmeService.copyProgramme(id, targetUsername);
            return ResponseEntity.ok("Program copied with ID: " + copiedSeance.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error while copying: " + e.getMessage());
        }
    }

    /**
     * Persists a new manual display order for the user's programmes (drag-and-drop reorder).
     *
     * @param username   The username of the requester.
     * @param orderedIds The programme IDs in the desired display order (first ID → top).
     * @return A standard empty successful ResponseEntity, or an error if unauthorized.
     */
    @PutMapping("/order")
    public ResponseEntity<?> reorderProgrammes(@RequestParam String username, @RequestBody List<Long> orderedIds) {
        try {
            programmeService.reorderProgrammes(username, orderedIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Deletes a specific workout program.
     *
     * @param id       The ID of the program to delete.
     * @param username The username of the requester.
     * @return A standard empty successful ResponseEntity, or an error if unauthorized.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProgramme(@PathVariable Long id, @RequestParam String username) {
        try {
            programmeService.deleteProgramme(id, username);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
