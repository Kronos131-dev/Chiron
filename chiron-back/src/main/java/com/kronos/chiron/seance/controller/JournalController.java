package com.kronos.chiron.seance.controller;

import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.mapper.SeanceMapper;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/journal")
public class JournalController {

    private final SeanceRepository seanceRepository;
    private final SeanceMapper seanceMapper;

    public JournalController(SeanceRepository seanceRepository, SeanceMapper seanceMapper) {
        this.seanceRepository = seanceRepository;
        this.seanceMapper = seanceMapper;
    }

    @GetMapping("/historique")
    public ResponseEntity<List<SeanceDto>> getHistorique(@RequestParam String username) {
        List<Seance> historique = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueTrueOrderByStartTimeDesc(username);
        List<SeanceDto> dtos = historique.stream().map(seanceMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
