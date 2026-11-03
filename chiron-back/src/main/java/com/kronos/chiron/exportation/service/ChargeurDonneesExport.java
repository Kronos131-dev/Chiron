package com.kronos.chiron.exportation.service;

import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.exportation.model.ExportProgramme;
import com.kronos.chiron.exportation.model.ExportProgrammeModele;
import com.kronos.chiron.exportation.model.ExportProgrammeSeance;
import com.kronos.chiron.exportation.service.classeur.DonneesExport;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.DegressifDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.ExerciceDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.GenreExercice;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.ModeleDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SeanceDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SerieDonnees;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChargeurDonneesExport {

    private final SeanceRepository seanceRepository;
    private final RattachementParTitre rattachementParTitre;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DonneesExport charger(ExportProgramme export) {
        Long utilisateurId = export.getUtilisateur().getId();
        List<ExportProgrammeModele> lignesModeles = export.getModeles();
        List<Long> modeleIds = lignesModeles.stream().map(ExportProgrammeModele::getModeleId).toList();

        Map<Long, Seance> modeles = new HashMap<>();
        seanceRepository.findAllById(modeleIds).forEach(m -> modeles.put(m.getId(), m));

        Map<Long, Seance> faites = new LinkedHashMap<>();
        Map<Long, Long> ongletParSeance = new HashMap<>();
        List<Long> ajoutees = export.getSeances().stream().map(ExportProgrammeSeance::getSeanceId).toList();
        if (!ajoutees.isEmpty()) {
            for (Seance seance : seanceRepository.findCompletesDeLUtilisateur(utilisateurId, ajoutees)) {
                faites.put(seance.getId(), seance);
            }
            for (ExportProgrammeSeance ligne : export.getSeances()) {
                ongletParSeance.put(ligne.getSeanceId(), ligne.getModeleId());
            }
        }
        if (!modeleIds.isEmpty()) {
            for (Seance seance : seanceRepository.findCompletesParModele(modeleIds)) {
                faites.putIfAbsent(seance.getId(), seance);
                ongletParSeance.putIfAbsent(seance.getId(), seance.getModeleId());
            }
        }

        rattacherParTitre(utilisateurId, lignesModeles, modeles, faites, ongletParSeance);

        List<ModeleDonnees> donneesModeles = new ArrayList<>();
        for (ExportProgrammeModele ligne : lignesModeles) {
            Seance modele = modeles.get(ligne.getModeleId());
            if (modele == null) continue;
            List<SeanceDonnees> seances = faites.values().stream()
                    .filter(s -> ligne.getModeleId().equals(ongletParSeance.get(s.getId())))
                    .filter(s -> !s.getExercices().isEmpty())
                    .sorted(Comparator.comparing(Seance::getStartTime))
                    .map(this::seanceDonnees)
                    .toList();
            donneesModeles.add(new ModeleDonnees(ligne.getModeleId(), modele.getTitre(),
                    ordreDesExercices(modele), seances));
        }
        return new DonneesExport(export.getNom(), LocalDateTime.now(clock), donneesModeles);
    }

    private void rattacherParTitre(Long utilisateurId, List<ExportProgrammeModele> lignesModeles,
            Map<Long, Seance> modeles, Map<Long, Seance> faites, Map<Long, Long> ongletParSeance) {
        Map<Long, String> titres = new LinkedHashMap<>();
        for (ExportProgrammeModele ligne : lignesModeles) {
            Seance modele = modeles.get(ligne.getModeleId());
            if (modele != null) {
                titres.put(ligne.getModeleId(), modele.getTitre());
            }
        }
        Map<Long, Long> parTitre = rattachementParTitre.chercher(utilisateurId, titres);
        List<Long> aCharger = parTitre.keySet().stream().filter(id -> !faites.containsKey(id)).toList();
        if (aCharger.isEmpty()) {
            return;
        }
        for (Seance seance : seanceRepository.findCompletesDeLUtilisateur(utilisateurId, aCharger)) {
            faites.putIfAbsent(seance.getId(), seance);
            ongletParSeance.putIfAbsent(seance.getId(), parTitre.get(seance.getId()));
        }
    }

    private List<String> ordreDesExercices(Seance modele) {
        Set<String> cles = new LinkedHashSet<>();
        modele.getExercices().stream()
                .sorted(Comparator.comparingInt(Exercice::getDisplayOrder))
                .forEach(exercice -> cles.add(cle(exercice)));
        return new ArrayList<>(cles);
    }

    private SeanceDonnees seanceDonnees(Seance seance) {
        List<ExerciceDonnees> exercices = seance.getExercices().stream()
                .sorted(Comparator.comparingInt(Exercice::getDisplayOrder))
                .map(this::exerciceDonnees)
                .toList();
        return new SeanceDonnees(seance.getId(), seance.getTitre(), seance.getStartTime(), seance.getNote(),
                exercices);
    }

    private ExerciceDonnees exerciceDonnees(Exercice exercice) {
        List<SerieDonnees> series = exercice.getSeries().stream().map(this::serieDonnees).toList();
        return new ExerciceDonnees(cle(exercice), exercice.getNom(), exercice.getCommentaire(),
                genre(exercice, series), series);
    }

    private SerieDonnees serieDonnees(Serie serie) {
        List<DegressifDonnees> degressifs = serie.getDegressifs().stream()
                .map(d -> new DegressifDonnees(d.getPoids(), d.getNombreReps()))
                .toList();
        return new SerieDonnees(serie.getPoids(), serie.getNombreReps(), serie.getDureeMin(), serie.getDistanceM(),
                degressifs);
    }

    private GenreExercice genre(Exercice exercice, List<SerieDonnees> series) {
        if (exercice.getDefinition() != null) {
            if (exercice.getDefinition().getWodType() != null) return GenreExercice.WOD;
            if (exercice.getDefinition().getCardioType() != null) return GenreExercice.CARDIO;
            return GenreExercice.MUSCU;
        }
        boolean cardio = series.stream().anyMatch(s -> (s.distanceM() != null && s.distanceM() > 0)
                || (s.dureeMin() != null && s.dureeMin() > 0));
        return cardio ? GenreExercice.CARDIO : GenreExercice.MUSCU;
    }

    private String cle(Exercice exercice) {
        if (exercice.getDefinition() != null) {
            return "d" + exercice.getDefinition().getId();
        }
        String nom = exercice.getNom() == null ? "" : exercice.getNom();
        String sansAccent = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return "n" + sansAccent.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
