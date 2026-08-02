package com.kronos.chiron.coach.tools;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.stats.MuscleStatsDto;
import com.kronos.chiron.stats.StatsOverviewDto;
import com.kronos.chiron.stats.StatsService;
import com.kronos.chiron.visbody.BodyCompositionRecord;
import com.kronos.chiron.visbody.BodyCompositionRecordRepository;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;

/**
 * Outils LangChain4j donnant à Chiron le contexte nécessaire à une analyse diététique
 * approfondie : profil personnel (sexe, âge, taille, poids, objectif), composition
 * corporelle (scans Visbody) et charge d'entraînement récente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyseDieteTools {

    private final UtilisateurRepository utilisateurRepository;
    private final BodyCompositionRecordRepository bodyCompositionRepository;
    private final StatsService statsService;

    @Tool("Récupère le profil personnel et sportif : sexe, âge, taille, poids de corps, objectif principal, niveau d'expérience, fréquence visée, blessures. Base pour estimer les besoins caloriques.")
    public String getProfilSportif(@ToolMemoryId String userId) {
        Utilisateur u = loadUser(userId);
        StringBuilder res = new StringBuilder("Profil personnel et sportif :\n");
        res.append("- Sexe : ").append(u.getSexe() != null ? u.getSexe() : "non renseigné").append("\n");
        if (u.getDateNaissance() != null) {
            int age = Period.between(u.getDateNaissance(), LocalDate.now()).getYears();
            res.append("- Âge : ").append(age).append(" ans\n");
        } else {
            res.append("- Âge : non renseigné\n");
        }
        res.append("- Taille : ").append(u.getTailleCm() != null ? fmt1(u.getTailleCm()) + " cm" : "non renseignée").append("\n");
        res.append("- Poids de corps : ").append(u.getPoidsCorps() != null ? fmt1(u.getPoidsCorps()) + " kg" : "non renseigné").append("\n");
        res.append("- Objectif principal : ").append(u.getObjectifPrincipal() != null ? u.getObjectifPrincipal() : "non défini").append("\n");
        res.append("- Niveau d'expérience : ").append(u.getNiveauExperience() != null ? u.getNiveauExperience() : "non défini").append("\n");
        res.append("- Fréquence d'entraînement visée : ")
                .append(u.getFrequenceVisee() != null ? u.getFrequenceVisee() + " séances/semaine" : "non définie").append("\n");
        if (u.getBlessures() != null && !u.getBlessures().isBlank()) {
            res.append("- Blessures : ").append(u.getBlessures()).append("\n");
        }
        return res.toString();
    }

    @Tool("Récupère la dernière composition corporelle (scan Visbody) : poids, masse grasse, masse musculaire, masse maigre, métabolisme de base (kcal/j), graisse viscérale, âge métabolique, et tendance vs scan précédent. Le métabolisme de base sert de référence pour la dépense énergétique.")
    public String getCompositionCorporelle(@ToolMemoryId String userId) {
        Utilisateur u = loadUser(userId);
        List<BodyCompositionRecord> scans = bodyCompositionRepository.findByUtilisateurOrderByMesureLeAsc(u);
        if (scans.isEmpty()) {
            return "Aucun scan de composition corporelle (Visbody) disponible pour cet utilisateur.";
        }
        BodyCompositionRecord last = scans.get(scans.size() - 1);
        BodyCompositionRecord prev = scans.size() >= 2 ? scans.get(scans.size() - 2) : null;

        StringBuilder res = new StringBuilder("Composition corporelle (dernier scan Visbody du ")
                .append(last.getMesureLe().toLocalDate()).append(") :\n");
        res.append("- Poids : ").append(metric(last.getPoids(), "kg", prev != null ? prev.getPoids() : null)).append("\n");
        res.append("- Masse grasse : ").append(metric(last.getTgcPct(), "%", prev != null ? prev.getTgcPct() : null)).append("\n");
        res.append("- Masse musculaire : ").append(metric(last.getMasseMusculaire(), "kg", prev != null ? prev.getMasseMusculaire() : null)).append("\n");
        res.append("- Masse maigre : ").append(metric(last.getMmc(), "kg", null)).append("\n");
        res.append("- Métabolisme de base : ").append(metric(last.getMbKcal(), "kcal/jour", null)).append("\n");
        res.append("- Graisse viscérale : ").append(metric(last.getGraisseViscerale(), "", null)).append("\n");
        res.append("- Âge métabolique : ").append(metric(last.getAgeMetabolique(), "ans", null)).append("\n");
        if (prev != null) {
            res.append("(Tendance calculée par rapport au scan du ").append(prev.getMesureLe().toLocalDate()).append(".)");
        }
        return res.toString();
    }

    @Tool("Récupère un bilan de la charge d'entraînement récente de l'utilisateur : nombre de séances sur 30 jours, tonnage de la semaine, durée moyenne, série de semaines actives, et groupes musculaires négligés sur la période. Sert à estimer la dépense énergétique liée à l'activité.")
    public String getChargeEntrainement(@ToolMemoryId String userId, Integer nbJours) {
        Utilisateur u = loadUser(userId);
        int window = (nbJours != null && nbJours > 0) ? nbJours : 30;
        StatsOverviewDto overview = statsService.getOverview(u.getUsername());

        StringBuilder res = new StringBuilder("Charge d'entraînement récente :\n");
        res.append("- Séances sur 30 jours : ").append(overview.seances30()).append("\n");
        res.append("- Tonnage de la semaine : ").append(fmt0(overview.tonnageSemaine())).append(" kg\n");
        if (overview.dureeMoyenneMin() != null) {
            res.append("- Durée moyenne d'une séance : ").append(fmt0(overview.dureeMoyenneMin())).append(" min\n");
        }
        res.append("- Semaines actives consécutives : ").append(overview.streakSemaines()).append("\n");
        res.append("- Palier global : ").append(overview.tier()).append("\n");

        MuscleStatsDto muscles = statsService.getMuscleStats(u.getUsername(), window);
        if (muscles.negliges() != null && !muscles.negliges().isEmpty()) {
            res.append("- Muscles négligés sur ").append(window).append(" j : ")
                    .append(String.join(", ", muscles.negliges())).append("\n");
        }
        return res.toString();
    }

    // --- Helpers ---

    private Utilisateur loadUser(String userId) {
        return utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
    }

    /** Formate une valeur (avec unité) et, si une valeur précédente est fournie, la tendance. */
    private String metric(Double value, String unit, Double previous) {
        if (value == null) {
            return "non mesuré";
        }
        String suffix = unit.isBlank() ? "" : " " + unit;
        StringBuilder sb = new StringBuilder(fmt1(value)).append(suffix);
        if (previous != null) {
            double delta = value - previous;
            if (Math.abs(delta) >= 0.05) {
                sb.append(" (").append(delta > 0 ? "+" : "").append(fmt1(delta)).append(suffix).append(")");
            }
        }
        return sb.toString();
    }

    private String fmt0(double v) {
        return String.format(Locale.FRANCE, "%.0f", v);
    }

    private String fmt1(double v) {
        return String.format(Locale.FRANCE, "%.1f", v);
    }
}
