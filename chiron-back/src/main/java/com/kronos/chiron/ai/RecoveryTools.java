package com.kronos.chiron.ai;

import com.kronos.chiron.entity.EtatJournalier;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.service.RecoveryService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Outils donnant à Chiron la lecture/écriture de l'état journalier de l'utilisateur
 * (sommeil, fatigue, courbatures, stress, énergie) et la capacité à recommander
 * un type de séance adapté à l'état actuel.
 */
@Component
@RequiredArgsConstructor
public class RecoveryTools {

    private final UtilisateurRepository utilisateurRepository;
    private final RecoveryService recoveryService;

    @Tool("Enregistre ou met à jour l'état du jour de l'utilisateur : sommeil (heures), fatigue/courbatures/stress/énergie (échelles 1-5 où 1 = au mieux pour fatigue/courbatures/stress et 5 = au pire ; pour energie c'est l'inverse : 1 = vide, 5 = à fond), notes libres. La date est optionnelle (format AAAA-MM-JJ ; défaut = aujourd'hui). Tous les paramètres sont optionnels — n'enregistre que ce que l'utilisateur a mentionné. À appeler dès que l'utilisateur parle de son sommeil, de sa fatigue, de ses courbatures ou de son énergie.")
    public String enregistrerEtatJournalier(@MemoryId String userId,
                                             String date,
                                             Double sommeilHeures,
                                             Integer fatigue,
                                             Integer courbatures,
                                             Integer stress,
                                             Integer energie,
                                             String notes) {
        Utilisateur user = loadUser(userId);
        LocalDate target;
        if (date == null || date.isBlank()) {
            target = LocalDate.now();
        } else {
            try {
                target = LocalDate.parse(date.trim());
            } catch (DateTimeParseException e) {
                return "Date invalide '" + date + "'. Format attendu AAAA-MM-JJ.";
            }
        }
        recoveryService.upsert(user, target, sommeilHeures, fatigue, courbatures, stress, energie, notes);
        return "État du " + target + " enregistré.";
    }

    @Tool("Récupère l'état journalier (sommeil, fatigue, courbatures, stress, énergie) de l'utilisateur sur les N derniers jours (défaut 7, max 90). À consulter avant de recommander une séance lourde, ou quand l'utilisateur s'interroge sur sa forme.")
    public String getEtatRecent(@MemoryId String userId, Integer nbJours) {
        Utilisateur user = loadUser(userId);
        int window = (nbJours != null && nbJours > 0) ? nbJours : 7;
        List<EtatJournalier> etats = recoveryService.getRecent(user, window);

        if (etats.isEmpty()) {
            return "Aucun état enregistré sur les " + window + " derniers jours. Demande à l'utilisateur comment il se sent.";
        }

        StringBuilder res = new StringBuilder("État sur les ").append(window).append(" derniers jours (")
                .append(etats.size()).append(" jour(s) renseignés) :\n");
        for (EtatJournalier e : etats) {
            res.append("- ").append(e.getDate()).append(" : ");
            res.append(joinNonNull(
                    e.getSommeilHeures() != null ? "sommeil " + e.getSommeilHeures() + "h" : null,
                    e.getFatigue() != null       ? "fatigue " + e.getFatigue() + "/5" : null,
                    e.getCourbatures() != null   ? "courb " + e.getCourbatures() + "/5" : null,
                    e.getStress() != null        ? "stress " + e.getStress() + "/5" : null,
                    e.getEnergie() != null       ? "énergie " + e.getEnergie() + "/5" : null
            ));
            if (e.getNotes() != null && !e.getNotes().isBlank()) {
                res.append(" — ").append(e.getNotes());
            }
            res.append("\n");
        }
        return res.toString();
    }

    @Tool("Renvoie une recommandation de type de séance (lourd / modéré / léger / repos) en croisant l'état du jour et les 3 derniers jours. À appeler quand l'utilisateur demande quoi faire aujourd'hui ou avant de proposer un programme intense.")
    public String recommanderTypeSeance(@MemoryId String userId) {
        Utilisateur user = loadUser(userId);
        List<EtatJournalier> etats = recoveryService.getRecent(user, 3);

        if (etats.isEmpty()) {
            return "Pas de données récentes pour évaluer la forme. Demande à l'utilisateur comment il se sent (sommeil, fatigue, courbatures) avant de recommander.";
        }

        EtatJournalier today = etats.get(0);

        // Signaux d'alerte forte
        if (today.getFatigue() != null && today.getFatigue() >= 5) {
            return "Recommandation : REPOS ou mobilité légère. Justification : fatigue déclarée à 5/5 aujourd'hui.";
        }
        if (today.getSommeilHeures() != null && today.getSommeilHeures() < 5.0) {
            return "Recommandation : séance LÉGÈRE (~50-60 % de l'intensité habituelle) ou repos actif. Justification : sommeil < 5h la nuit dernière.";
        }
        if (today.getCourbatures() != null && today.getCourbatures() >= 4) {
            return "Recommandation : séance LÉGÈRE ciblant d'autres groupes musculaires, ou mobilité. Justification : courbatures importantes (" + today.getCourbatures() + "/5).";
        }

        // Moyennes sur les 3 jours
        double avgFatigue = avg(etats, EtatJournalier::getFatigue);
        double avgSommeil = avg(etats, e -> e.getSommeilHeures() != null ? (int) Math.round(e.getSommeilHeures()) : null);
        double avgEnergie = avg(etats, EtatJournalier::getEnergie);

        if (avgFatigue >= 3.5) {
            return String.format("Recommandation : séance MODÉRÉE (70-80 %% intensité). Justification : fatigue moyenne %.1f/5 sur 3 jours.", avgFatigue);
        }
        if (avgEnergie > 0 && avgEnergie >= 4) {
            return String.format("Recommandation : tout est vert, séance LOURDE possible. Justification : énergie moyenne %.1f/5, fatigue %.1f/5.", avgEnergie, avgFatigue);
        }
        if (avgSommeil > 0 && avgSommeil < 6) {
            return String.format("Recommandation : séance MODÉRÉE. Justification : sommeil moyen ~%.1fh sur 3 jours.", avgSommeil);
        }
        return "Recommandation : séance NORMALE (intensité habituelle). Aucun signal alarmant détecté.";
    }

    private double avg(List<EtatJournalier> etats, java.util.function.Function<EtatJournalier, Integer> extractor) {
        int sum = 0, n = 0;
        for (EtatJournalier e : etats) {
            Integer v = extractor.apply(e);
            if (v != null) { sum += v; n++; }
        }
        return n > 0 ? (double) sum / n : 0.0;
    }

    private String joinNonNull(String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (p == null) continue;
            if (!first) sb.append(", ");
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }

    private Utilisateur loadUser(String userId) {
        return utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
    }
}
