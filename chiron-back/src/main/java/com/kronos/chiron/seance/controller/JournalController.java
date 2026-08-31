package com.kronos.chiron.seance.controller;

import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.dto.ExerciceDto;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.mapper.SeanceMapper;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.seance.persistence.ExerciceRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalController {

    private final SeanceRepository seanceRepository;
    private final ExerciceRepository exerciceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SeanceMapper seanceMapper;
    private final Clock clock;

    @GetMapping("/historique")
    public ResponseEntity<List<SeanceDto>> getHistorique(@RequestParam String username) {
        List<Seance> historique = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueTrueOrderByStartTimeDesc(username);
        List<SeanceDto> dtos = historique.stream().map(seanceMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/last-performance")
    public ResponseEntity<ExerciceDto> getLastPerformance(@RequestParam String username,
            @RequestParam Long definitionId) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found"));

        LocalDateTime since = LocalDateTime.now(clock).minusWeeks(6);
        Optional<Exercice> lastExoOpt = exerciceRepository.findLastPerformance(user.getId(), definitionId, since);

        if (lastExoOpt.isEmpty()) {
            throw notFound("No recent performance found for this exercise");
        }

        return ResponseEntity.ok(seanceMapper.toExerciceDto(lastExoOpt.get()));
    }
}
