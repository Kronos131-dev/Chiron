package com.kronos.chiron.seance.service.impl;

import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.seance.service.SeanceResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeanceResumeServiceImpl implements SeanceResumeService {

    private static final String SANS_NOM = "Sans nom";

    private final SeanceRepository seanceRepository;

    @Override
    @Transactional(readOnly = true)
    public String decrireContenu(Long seanceId) {
        if (seanceId == null) return "";
        Seance seance = seanceRepository.findById(seanceId).orElse(null);
        if (seance == null) return "";

        List<Exercice> exercices = seance.getExercices();
        if (exercices == null || exercices.isEmpty()) return "";

        String detail = exercices.stream()
                .map(this::decrireExercice)
                .collect(Collectors.joining(" ; "));

        int nbSeries = exercices.stream()
                .mapToInt(e -> e.getSeries() == null ? 0 : e.getSeries().size())
                .sum();

        return "Contenu de la séance '" + titre(seance) + "' : " + detail + ". " + nbSeries
                + " série(s) au total.";
    }

    private String decrireExercice(Exercice exercice) {
        String label = exercice.isUnilateral() ? exercice.getNom() + " (unilatéral)" : exercice.getNom();
        List<Serie> series = exercice.getSeries();
        if (series == null || series.isEmpty()) return label + " : aucune série";
        return label + " : " + series.stream().map(this::decrireSerie).collect(Collectors.joining(" | "));
    }

    private String decrireSerie(Serie serie) {
        String detail = String.format(Locale.FRANCE, "%d reps @ %.1fkg", serie.getNombreReps(), serie.getPoids());
        if (serie.getDegressifs() != null && !serie.getDegressifs().isEmpty()) {
            detail += " + " + serie.getDegressifs().size() + " dégressif(s)";
        }
        return detail;
    }

    private String titre(Seance seance) {
        return seance.getTitre() != null && !seance.getTitre().isBlank() ? seance.getTitre() : SANS_NOM;
    }
}
