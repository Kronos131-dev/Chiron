package com.kronos.chiron.programme.controller;

import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.mapper.SeanceMapper;
import com.kronos.chiron.programme.service.ProgrammeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/programmes")
@RequiredArgsConstructor
public class ProgrammeController {

    private final ProgrammeService programmeService;
    private final SeanceMapper seanceMapper;

    @GetMapping
    public ResponseEntity<List<SeanceDto>> getProgrammes(@RequestParam String username) {
        List<Seance> programmes = programmeService.getProgrammes(username);
        List<SeanceDto> dtos = programmes.stream().map(seanceMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<?> creerProgramme(@RequestParam String username,
                                            @RequestParam(required = false) String forUsername,
                                            @RequestBody SeanceDto seanceDto) {
        try {
            Seance savedSeance = programmeService.sauvegarderProgramme(username, seanceDto, forUsername);
            return ResponseEntity.ok("Program saved with ID: " + savedSeance.getId());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error while saving: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProgrammeById(@PathVariable Long id, @RequestParam String username) {
        try {
            Seance seance = programmeService.getProgrammeById(id, username);
            return ResponseEntity.ok(seanceMapper.toDto(seance));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

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

    @PutMapping("/order")
    public ResponseEntity<?> reorderProgrammes(@RequestParam String username, @RequestBody List<Long> orderedIds) {
        try {
            programmeService.reorderProgrammes(username, orderedIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

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
