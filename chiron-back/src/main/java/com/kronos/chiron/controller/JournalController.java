package com.kronos.chiron.controller;

import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.mapper.SeanceMapper;
import com.kronos.chiron.repository.SeanceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for retrieving workout journal data.
 * Provides endpoints for fetching user workout history.
 */
@RestController
@RequestMapping("/api/journal")
public class JournalController {

    private final SeanceRepository seanceRepository;
    private final SeanceMapper seanceMapper;

    /**
     * Constructs a new JournalController.
     *
     * @param seanceRepository Repository for fetching Seance entities.
     * @param seanceMapper     Mapper to convert Seance entities to Data Transfer Objects.
     */
    public JournalController(SeanceRepository seanceRepository, SeanceMapper seanceMapper) {
        this.seanceRepository = seanceRepository;
        this.seanceMapper = seanceMapper;
    }

    /**
     * Endpoint to fetch the full workout history for a given user.
     * Filters for actual completed sessions (isModele = true) and returns them in descending chronological order.
     *
     * @param username The username of the user whose history is being requested.
     * @return A ResponseEntity containing a list of SeanceDto representing the historical sessions.
     */
    @GetMapping("/historique")
    public ResponseEntity<List<SeanceDto>> getHistorique(@RequestParam String username) {
        List<Seance> historique = seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(username);
        List<SeanceDto> dtos = historique.stream().map(seanceMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
