package com.kronos.chiron.coach.tools;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.exercice.dto.ExerciceDefinitionDto;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.exercice.model.ExerciceDefinition;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.seance.persistence.ExerciceRepository;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.exercice.service.ExerciceDefinitionService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Outils donnant à Chiron la capacité de raisonner sur la PROGRESSION et la PÉRIODISATION
 * du travail de l'utilisateur, et de proposer des rotations d'exercices après une longue
 * exposition au même mouvement.
 */
@Component
@RequiredArgsConstructor
public class AdaptiveTools {

    private static final int ROTATION_WEEKS_THRESHOLD = 8;
    private static final int PERIODISATION_SESSIONS_WINDOW = 6;

    private final UtilisateurRepository utilisateurRepository;
    private final ExerciceRepository exerciceRepository;
    private final SeanceRepository seanceRepository;
    private final ExerciceDefinitionService exerciceDefinitionService;

    @Tool("Propose la prochaine progression sur un exercice d'après la dernière séance (cohérence des charges, drop-off, comparaison N-1). Renvoie une reco chiffrée (+2.5 kg, maintien, baisse, +1 rep…).")
    public String getProgressionSuggeree(@ToolMemoryId String userId, String nomExercice) {
        if (nomExercice == null || nomExercice.isBlank()) {
            return "Nom d'exercice manquant.";
        }
        Long uid = Long.parseLong(userId);

        List<Exercice> historique = exerciceRepository.findAllHistoricExercises(uid, nomExercice);
        if (historique.isEmpty()) {
            return "Aucune donnée historique sur '" + nomExercice + "'. Impossible de suggérer une progression.";
        }
        List<Exercice> std = historique.stream().filter(e -> e.getDefinition() != null).collect(Collectors.toList());
        if (std.isEmpty()) {
            return "L'exercice '" + nomExercice + "' n'est pas dans la base standardisée [std]. Suggestion de progression indisponible.";
        }

        Exercice derniere = std.get(0);
        List<Serie> seriesD = derniere.getSeries() != null ? derniere.getSeries() : List.of();
        if (seriesD.isEmpty()) {
            return "La dernière séance de '" + nomExercice + "' n'a aucune série enregistrée.";
        }

        double maxCharge = seriesD.stream().mapToDouble(Serie::getPoids).max().orElse(0);
        double minCharge = seriesD.stream().mapToDouble(Serie::getPoids).min().orElse(0);
        int maxReps = seriesD.stream().mapToInt(Serie::getNombreReps).max().orElse(0);
        int minReps = seriesD.stream().mapToInt(Serie::getNombreReps).min().orElse(0);
        boolean chargeStable = maxCharge - minCharge < 0.01;
        boolean repsStables = (maxReps - minReps) <= 1;

        Exercice precedente = std.size() >= 2 ? std.get(1) : null;
        double max1RMderniere = best1RM(seriesD);
        Double max1RMprec = precedente != null ? best1RM(precedente.getSeries()) : null;

        StringBuilder res = new StringBuilder();
        res.append("Dernière séance de '").append(nomExercice).append("' : ")
                .append(seriesD.size()).append(" séries, charges ");
        if (chargeStable) {
            res.append(maxCharge).append(" kg");
        } else {
            res.append(minCharge).append("→").append(maxCharge).append(" kg");
        }
        res.append(", reps ");
        if (repsStables) {
            res.append(maxReps);
        } else {
            res.append(minReps).append("→").append(maxReps);
        }
        res.append(". 1RM estimé ").append(String.format("%.1f", max1RMderniere)).append(" kg.\n");

        if (max1RMprec != null) {
            double delta = max1RMderniere - max1RMprec;
            if (delta > 0.5) {
                res.append("Progression vs séance précédente : +").append(String.format("%.1f", delta)).append(" kg de 1RM. ");
            } else if (delta < -0.5) {
                res.append("Régression vs séance précédente : ").append(String.format("%.1f", delta)).append(" kg de 1RM. ");
            } else {
                res.append("Stable vs séance précédente (1RM ").append(String.format("%.1f", max1RMprec)).append(" → ").append(String.format("%.1f", max1RMderniere)).append("). ");
            }
        }

        if (chargeStable && repsStables && minReps >= 8) {
            res.append("Recommandation : +2.5 kg sur la charge de travail dès la prochaine séance (séries propres, reps cible atteinte).");
        } else if (chargeStable && repsStables && minReps >= 5) {
            res.append("Recommandation : viser +1 à +2 reps sur chaque série avant de monter la charge.");
        } else if (!chargeStable || !repsStables) {
            res.append("Recommandation : maintenir la charge actuelle. Drop-off détecté (séries non homogènes), consolide avant d'ajouter du poids.");
        } else {
            res.append("Recommandation : maintenir et viser une exécution plus propre avant d'augmenter.");
        }
        return res.toString();
    }

    @Tool("Analyse la périodisation sur un exercice : 1RM estimé (Epley) des 6 dernières séances, moyenne des 3 récentes vs 3 anciennes. Diagnostique progression, plateau (< 2 %) ou régression (deload conseillé).")
    public String analyserPeriodisation(@ToolMemoryId String userId, String nomExercice) {
        if (nomExercice == null || nomExercice.isBlank()) {
            return "Nom d'exercice manquant.";
        }
        Long uid = Long.parseLong(userId);

        List<Exercice> historique = exerciceRepository.findAllHistoricExercises(uid, nomExercice);
        List<Exercice> std = historique.stream().filter(e -> e.getDefinition() != null).collect(Collectors.toList());
        if (std.isEmpty()) {
            return "Aucune donnée [std] sur '" + nomExercice + "'. Analyse de périodisation impossible.";
        }
        if (std.size() < 3) {
            return "Pas assez de séances [std] sur '" + nomExercice + "' (seulement " + std.size() + "). Il faut au moins 3 séances pour analyser une tendance.";
        }

        List<Exercice> window = std.stream().limit(PERIODISATION_SESSIONS_WINDOW).collect(Collectors.toList());

        List<Double> rms = new ArrayList<>();
        for (Exercice e : window) {
            rms.add(best1RM(e.getSeries()));
        }

        int n = rms.size();
        int half = n / 2;
        // rms est en ordre desc (plus récent en tête) → recent = 0..half-1, early = n-half..n-1
        double avgRecent = avg(rms.subList(0, half));
        double avgEarly = avg(rms.subList(n - half, n));

        double deltaPct = avgEarly > 0 ? (avgRecent - avgEarly) / avgEarly * 100.0 : 0;

        StringBuilder res = new StringBuilder();
        res.append("Périodisation sur '").append(nomExercice).append("' (").append(n).append(" séances analysées) :\n");
        res.append("- 1RM moyen ").append(half).append(" récentes : ").append(String.format("%.1f", avgRecent)).append(" kg\n");
        res.append("- 1RM moyen ").append(half).append(" anciennes : ").append(String.format("%.1f", avgEarly)).append(" kg\n");
        res.append("- Variation : ").append(String.format("%+.1f", deltaPct)).append(" %\n");

        if (deltaPct > 5) {
            res.append("Diagnostic : PROGRESSION CONTINUE. Maintiens la trajectoire actuelle.");
        } else if (deltaPct >= -2) {
            res.append("Diagnostic : PLATEAU (variation < 2 %). Envisage un changement de schéma (reps cibles différentes, variante d'exercice, semaine plus légère puis push).");
        } else if (deltaPct >= -8) {
            res.append("Diagnostic : LÉGÈRE RÉGRESSION. Vérifie sommeil, alimentation, stress. Une semaine de deload (charges -20 %) peut débloquer.");
        } else {
            res.append("Diagnostic : RÉGRESSION MARQUÉE. Overreaching probable — recommande un deload de 1 semaine (volume -50 %, charges -20 %) avant de relancer.");
        }
        return res.toString();
    }

    @Tool("Identifie les exercices pratiqués de longue date (> 8 semaines) et propose 1-2 alternatives par exercice, même muscle et équipement compatible. nomProgramme optionnel pour cibler un programme (sinon tous).")
    public String proposerRotation(@ToolMemoryId String userId, String nomProgramme) {
        Utilisateur user = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> notFound("Utilisateur introuvable"));

        List<Seance> programmes = seanceRepository
                .findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(user.getUsername());

        if (nomProgramme != null && !nomProgramme.isBlank()) {
            String filtre = nomProgramme.toLowerCase();
            programmes = programmes.stream()
                    .filter(p -> p.getTitre() != null && p.getTitre().toLowerCase().contains(filtre))
                    .collect(Collectors.toList());
            if (programmes.isEmpty()) {
                return "Aucun programme correspondant à '" + nomProgramme + "'. Utilise [getUserProgrammes] pour voir la liste.";
            }
        }

        if (programmes.isEmpty()) {
            return "L'utilisateur n'a aucun programme enregistré.";
        }

        // Map: definition.id → (nom, definition)
        Map<Long, ExerciceDefinition> defsParId = new LinkedHashMap<>();
        for (Seance p : programmes) {
            if (p.getExercices() == null) continue;
            for (Exercice e : p.getExercices()) {
                if (e.getDefinition() != null) {
                    defsParId.putIfAbsent(e.getDefinition().getId(), e.getDefinition());
                }
            }
        }
        if (defsParId.isEmpty()) {
            return "Aucun exercice [std] dans le(s) programme(s) examiné(s). Rotation impossible.";
        }

        LocalDateTime cutoff = LocalDateTime.now().minusWeeks(ROTATION_WEEKS_THRESHOLD);
        List<Seance> historique = seanceRepository
                .findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername());

        // Pour chaque définition, trouver la première occurrence historique [std]
        Map<Long, LocalDateTime> premiereOccurrence = new LinkedHashMap<>();
        for (Seance s : historique) {
            if (s.getStartTime() == null || s.getExercices() == null) continue;
            for (Exercice e : s.getExercices()) {
                if (e.getDefinition() == null) continue;
                Long defId = e.getDefinition().getId();
                if (!defsParId.containsKey(defId)) continue;
                premiereOccurrence.merge(defId, s.getStartTime(),
                        (existing, candidate) -> existing.isBefore(candidate) ? existing : candidate);
            }
        }

        List<Map.Entry<Long, LocalDateTime>> candidats = premiereOccurrence.entrySet().stream()
                .filter(en -> en.getValue().isBefore(cutoff))
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .collect(Collectors.toList());

        if (candidats.isEmpty()) {
            return "Aucun exercice du(des) programme(s) n'est pratiqué depuis plus de " + ROTATION_WEEKS_THRESHOLD + " semaines. Pas de rotation prioritaire.";
        }

        Set<Long> alreadyUsedIds = new HashSet<>(defsParId.keySet());

        StringBuilder res = new StringBuilder();
        res.append(candidats.size()).append(" exercice(s) pratiqué(s) depuis plus de ")
                .append(ROTATION_WEEKS_THRESHOLD).append(" semaines, candidats à rotation :\n");

        for (Map.Entry<Long, LocalDateTime> en : candidats) {
            ExerciceDefinition def = defsParId.get(en.getKey());
            long semaines = ChronoUnit.WEEKS.between(en.getValue().toLocalDate(), LocalDateTime.now().toLocalDate());
            String nomDef = def.getNomFr() != null ? def.getNomFr() : def.getNomEn();
            res.append("- ").append(nomDef).append(" (depuis ").append(semaines).append(" semaines)");

            if (def.getMusclePrincipal() != null && def.getTypeEquipement() != null) {
                List<ExerciceDefinitionDto> alternatives = exerciceDefinitionService.search(
                        null,
                        def.getMusclePrincipal().name(),
                        def.getTypeEquipement().name(),
                        null);
                List<String> noms = alternatives.stream()
                        .filter(a -> !alreadyUsedIds.contains(a.id()))
                        .map(a -> a.nomFr() != null ? a.nomFr() : a.nomEn())
                        .limit(2)
                        .collect(Collectors.toList());
                if (!noms.isEmpty()) {
                    res.append(" → alternatives : ").append(String.join(", ", noms));
                }
            }
            res.append("\n");
        }
        return res.toString();
    }

    private static double best1RM(List<Serie> series) {
        if (series == null || series.isEmpty()) return 0;
        double best = 0;
        for (Serie s : series) {
            if (s.getNombreReps() <= 0) continue;
            double rm = s.getPoids() * (1 + s.getNombreReps() / 30.0);
            if (rm > best) best = rm;
        }
        return best;
    }

    private static double avg(List<Double> values) {
        if (values.isEmpty()) return 0;
        double sum = 0;
        int n = 0;
        for (Double v : values) {
            if (v != null && v > 0) { sum += v; n++; }
        }
        return n > 0 ? sum / n : 0;
    }
}
