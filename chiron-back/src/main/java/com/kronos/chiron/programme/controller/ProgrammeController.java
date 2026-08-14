package com.kronos.chiron.programme.controller;

import com.kronos.chiron.programme.service.ProgrammeService;
import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.mapper.SeanceMapper;
import com.kronos.chiron.seance.model.Seance;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/programmes")
@RequiredArgsConstructor
public class ProgrammeController {

    private final ProgrammeService programmeService;
    private final SeanceMapper seanceMapper;

    @GetMapping
    public ResponseEntity<List<SeanceDto>> getProgrammes(@RequestParam String username) {
        List<SeanceDto> dtos = programmeService.getProgrammes(username).stream()
                .map(seanceMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<String> creerProgramme(@RequestParam String username,
            @RequestParam(required = false) String forUsername,
            @RequestBody SeanceDto seanceDto) {
        Seance savedSeance = programmeService.sauvegarderProgramme(username, seanceDto, forUsername);
        return ResponseEntity.ok("Program saved with ID: " + savedSeance.getId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeanceDto> getProgrammeById(@PathVariable Long id, @RequestParam String username) {
        return ResponseEntity.ok(seanceMapper.toDto(programmeService.getProgrammeById(id, username)));
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<String> copyProgrammeToMyProfile(@PathVariable Long id,
            @RequestParam String targetUsername) {
        Seance copiedSeance = programmeService.copyProgramme(id, targetUsername);
        return ResponseEntity.ok("Program copied with ID: " + copiedSeance.getId());
    }

    @PutMapping("/order")
    public ResponseEntity<Void> reorderProgrammes(@RequestParam String username,
            @RequestBody List<Long> orderedIds) {
        programmeService.reorderProgrammes(username, orderedIds);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProgramme(@PathVariable Long id, @RequestParam String username) {
        programmeService.deleteProgramme(id, username);
        return ResponseEntity.ok().build();
    }
}
