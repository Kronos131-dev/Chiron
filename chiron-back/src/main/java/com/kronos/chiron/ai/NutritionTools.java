package com.kronos.chiron.ai;

import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.nutrition.NutritionService;
import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionCalculator;
import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionCalculator.NutritionTargets;
import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionDao;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.stats.BodyweightPointDto;
import com.kronos.chiron.stats.NutritionPointDto;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Outils LangChain4j donnant à Chiron accès aux données nutritionnelles de l'utilisateur.
 *
 * <p>Toutes les lectures se font directement en base Olympus (via {@link OlympusNutritionDao}),
 * par l'{@code olympus_user_id} résolu à la liaison — aucune dépendance à l'API HTTP d'Olympus.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NutritionTools {

    private static final String MSG_NON_LIE =
            "L'utilisateur n'a pas lié son compte Olympus à Chiron. Demande-lui de le faire depuis la page Réglages.";
    private static final String MSG_INDISPO =
            "Le service de nutrition est temporairement indisponible. Réessaie plus tard.";

    private final UtilisateurRepository utilisateurRepository;
    private final NutritionService nutritionService;
    private final OlympusNutritionDao olympusDao;
    private final OlympusNutritionCalculator calculator;

    @Tool("Récupère l'apport nutritionnel de l'utilisateur pour une date donnée (format YYYY-MM-DD ; null ou vide = aujourd'hui). Retourne calories, protéines, glucides, lipides consommés + cibles + delta + activité du jour. Nécessite que l'utilisateur ait lié son compte Olympus.")
    public String getApportJournalier(@ToolMemoryId String userId, String date) {
        Utilisateur user = loadUser(userId);
        LocalDate target;
        if (date == null || date.isBlank()) {
            target = LocalDate.now();
        } else {
            try {
                target = LocalDate.parse(date.trim());
            } catch (DateTimeParseException e) {
                return "Date invalide '" + date + "'. Utilise le format AAAA-MM-JJ ou laisse vide pour aujourd'hui.";
            }
        }

        Optional<Long> oid = nutritionService.getOlympusUserId(user.getUsername());
        if (oid.isEmpty()) return MSG_NON_LIE;

        try {
            Optional<OlympusNutritionDao.DailyLogRow> logOpt = olympusDao.dailyLog(oid.get(), target);
            if (logOpt.isEmpty()) {
                return "Aucun apport enregistré le " + target + ".";
            }
            OlympusNutritionDao.DailyLogRow log = logOpt.get();
            NutritionTargets cible = olympusDao.profile(oid.get())
                    .map(calculator::computeTargets).orElse(NutritionTargets.EMPTY);

            double consoKcal = orZero(log.totalKcal());
            double cibleKcal = orZero(cible.kcal());

            StringBuilder res = new StringBuilder();
            res.append("Apport du ").append(target).append(" :\n");
            res.append("- Calories : ").append(fmt(consoKcal)).append(" / ").append(fmt(cibleKcal))
                    .append(" kcal (").append(formatDelta(consoKcal - cibleKcal, "kcal")).append(")\n");
            res.append("- Protéines : ").append(fmt(orZero(log.totalProteins()))).append(" / ")
                    .append(fmt(orZero(cible.proteins()))).append(" g\n");
            res.append("- Glucides : ").append(fmt(orZero(log.totalCarbs()))).append(" / ")
                    .append(fmt(orZero(cible.carbs()))).append(" g\n");
            res.append("- Lipides : ").append(fmt(orZero(log.totalFats()))).append(" / ")
                    .append(fmt(orZero(cible.fats()))).append(" g\n");
            Integer pas = log.stepCount();
            Integer dureeMin = log.workoutDurationMinutes();
            double extraBrul = orZero(log.extraKcalBurned());
            if (pas != null && pas > 0) {
                res.append("Activité : ").append(pas).append(" pas");
                if (dureeMin != null && dureeMin > 0) {
                    res.append(", ").append(dureeMin).append(" min d'entraînement");
                }
                if (extraBrul > 0) {
                    res.append(", ~").append(fmt(extraBrul)).append(" kcal brûlées en plus");
                }
                res.append("\n");
            }
            return res.toString();
        } catch (DataAccessException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Récupère les objectifs nutritionnels de l'utilisateur : type d'objectif (perte / maintien / prise), cibles calories et macros, poids actuel. Nécessite la liaison Olympus.")
    public String getObjectifsNutritionnels(@ToolMemoryId String userId) {
        Utilisateur user = loadUser(userId);
        Optional<Long> oid = nutritionService.getOlympusUserId(user.getUsername());
        if (oid.isEmpty()) return MSG_NON_LIE;

        try {
            Optional<OlympusNutritionDao.ProfileRow> profOpt = olympusDao.profile(oid.get());
            if (profOpt.isEmpty()) return "Profil Olympus introuvable.";
            OlympusNutritionDao.ProfileRow prof = profOpt.get();
            NutritionTargets cible = calculator.computeTargets(prof);

            StringBuilder res = new StringBuilder("Profil nutritionnel :\n");
            res.append("- Objectif : ").append(goalFr(prof.goal())).append("\n");
            if (prof.currentWeightKg() != null && prof.currentWeightKg() > 0)
                res.append("- Poids actuel : ").append(fmt(prof.currentWeightKg())).append(" kg\n");
            if (prof.heightCm() != null && prof.heightCm() > 0)
                res.append("- Taille : ").append(fmt(prof.heightCm())).append(" cm\n");
            if (prof.activityLevel() != null)
                res.append("- Niveau d'activité : ").append(activityFr(prof.activityLevel())).append("\n");
            res.append("Cibles journalières :\n");
            res.append("- Calories : ").append(fmt(orZero(cible.kcal()))).append(" kcal\n");
            res.append("- Protéines : ").append(fmt(orZero(cible.proteins()))).append(" g\n");
            res.append("- Glucides : ").append(fmt(orZero(cible.carbs()))).append(" g\n");
            res.append("- Lipides : ").append(fmt(orZero(cible.fats()))).append(" g");
            return res.toString();
        } catch (DataAccessException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Analyse l'équilibre nutritionnel de l'utilisateur sur les N derniers jours (défaut 7) : moyenne calorique vs cible, répartition moyenne en macros, écart à l'objectif, perte de graisse estimée. Nécessite la liaison Olympus.")
    public String analyserEquilibreMacros(@ToolMemoryId String userId, Integer nbJours) {
        Utilisateur user = loadUser(userId);
        int window = (nbJours != null && nbJours > 0) ? nbJours : 7;
        Optional<Long> oid = nutritionService.getOlympusUserId(user.getUsername());
        if (oid.isEmpty()) return MSG_NON_LIE;

        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(window - 1L);
            List<NutritionPointDto> jours = olympusDao.dailyNutrition(oid.get(), start, end);

            int joursAvecDonnees = 0;
            double sumKcal = 0, sumProt = 0, sumGlu = 0, sumLip = 0;
            double sumCibleKcal = 0;
            int joursAvecCible = 0;
            double totalDeficit = 0; // somme (cible - conso) sur les jours ayant les deux
            for (NutritionPointDto pt : jours) {
                if (pt.kcal() != null) {
                    sumKcal += pt.kcal();
                    sumProt += orZero(pt.proteines());
                    sumGlu += orZero(pt.glucides());
                    sumLip += orZero(pt.lipides());
                    joursAvecDonnees++;
                    if (pt.targetKcal() != null) {
                        totalDeficit += pt.targetKcal() - pt.kcal();
                    }
                }
                if (pt.targetKcal() != null) {
                    sumCibleKcal += pt.targetKcal();
                    joursAvecCible++;
                }
            }

            if (joursAvecDonnees == 0) {
                return "Aucun apport enregistré sur les " + window + " derniers jours.";
            }

            double avgKcal = sumKcal / joursAvecDonnees;
            double avgProt = sumProt / joursAvecDonnees;
            double avgGlu = sumGlu / joursAvecDonnees;
            double avgLip = sumLip / joursAvecDonnees;
            double denom = avgProt * 4 + avgGlu * 4 + avgLip * 9;
            double pctProt = denom > 0 ? (avgProt * 4.0) / denom * 100 : 0;
            double pctGlu = denom > 0 ? (avgGlu * 4.0) / denom * 100 : 0;
            double pctLip = denom > 0 ? (avgLip * 9.0) / denom * 100 : 0;

            StringBuilder res = new StringBuilder();
            res.append("Bilan nutrition sur ").append(window).append(" jours (")
                    .append(joursAvecDonnees).append(" jour(s) avec données) :\n");
            res.append("- Apport moyen : ").append(fmt(avgKcal)).append(" kcal/jour");
            if (joursAvecCible > 0) {
                double avgCible = sumCibleKcal / joursAvecCible;
                double ecart = avgKcal - avgCible;
                double ecartPct = avgCible > 0 ? Math.abs(ecart) / avgCible * 100.0 : 0;
                res.append(" (cible ~").append(fmt(avgCible)).append(", ")
                        .append(formatDelta(ecart, "kcal")).append(", ")
                        .append(String.format(java.util.Locale.FRANCE, "%.0f", ecartPct)).append(" %)");
            }
            res.append("\n");
            res.append("- Répartition moyenne : ")
                    .append(String.format(java.util.Locale.FRANCE, "%.0f", pctProt)).append(" % prot / ")
                    .append(String.format(java.util.Locale.FRANCE, "%.0f", pctGlu)).append(" % glu / ")
                    .append(String.format(java.util.Locale.FRANCE, "%.0f", pctLip)).append(" % lip\n");
            res.append("- Macros moyens : ").append(fmt(avgProt)).append(" g prot, ")
                    .append(fmt(avgGlu)).append(" g glu, ").append(fmt(avgLip)).append(" g lip / jour\n");
            if (Math.abs(totalDeficit) > 50) {
                // 1 kg de graisse ≈ 7700 kcal → grammes = déficit kcal / 7.7
                double fatLossG = totalDeficit / 7.7;
                res.append("- Perte de graisse estimée sur la période : ").append(fmt(fatLossG)).append(" g");
            }
            return res.toString();
        } catch (DataAccessException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Récupère l'évolution du poids de l'utilisateur sur les N derniers jours (défaut 30) : première et dernière mesure, tendance (perte / prise / stable). Nécessite la liaison Olympus et que l'utilisateur se pèse régulièrement dans Olympus.")
    public String getEvolutionPoids(@ToolMemoryId String userId, Integer nbJours) {
        Utilisateur user = loadUser(userId);
        int window = (nbJours != null && nbJours > 0) ? nbJours : 30;
        Optional<Long> oid = nutritionService.getOlympusUserId(user.getUsername());
        if (oid.isEmpty()) return MSG_NON_LIE;

        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(window - 1L);
            List<BodyweightPointDto> mesures = olympusDao.weightHistory(oid.get(), start, end);
            if (mesures.isEmpty()) {
                return "Aucune mesure de poids enregistrée sur les " + window + " derniers jours.";
            }

            BodyweightPointDto premiere = mesures.get(0);
            BodyweightPointDto derniere = mesures.get(mesures.size() - 1);
            double sommePoids = 0, poidsMin = Double.MAX_VALUE, poidsMax = -Double.MAX_VALUE;
            for (BodyweightPointDto m : mesures) {
                sommePoids += m.poids();
                poidsMin = Math.min(poidsMin, m.poids());
                poidsMax = Math.max(poidsMax, m.poids());
            }

            double delta = derniere.poids() - premiere.poids();
            String tendance = Math.abs(delta) < 0.3 ? "stable" : (delta < 0 ? "perte" : "prise");
            int nbMesures = mesures.size();

            StringBuilder res = new StringBuilder();
            res.append("Évolution du poids sur ").append(window).append(" jours (")
                    .append(nbMesures).append(" mesure(s)) :\n");
            res.append("- Première mesure : ").append(fmtKg(premiere.poids()))
                    .append(" (le ").append(premiere.date()).append(")\n");
            res.append("- Dernière mesure : ").append(fmtKg(derniere.poids()))
                    .append(" (le ").append(derniere.date()).append(")\n");
            res.append("- Variation : ").append(formatDeltaKg(delta)).append(" (").append(tendance).append(")\n");
            if (nbMesures >= 3) {
                res.append("- Moyenne période : ").append(fmtKg(sommePoids / nbMesures)).append("\n");
                res.append("- Min/Max : ").append(fmtKg(poidsMin)).append(" / ").append(fmtKg(poidsMax));
            }
            return res.toString();
        } catch (DataAccessException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Récupère le planning de repas hebdomadaire de l'utilisateur (repas prévus jour par jour) ainsi que la liste de ses repas pré-enregistrés avec leurs valeurs nutritionnelles. Nécessite la liaison Olympus.")
    public String getPlanningRepas(@ToolMemoryId String userId) {
        Utilisateur user = loadUser(userId);
        Optional<Long> oid = nutritionService.getOlympusUserId(user.getUsername());
        if (oid.isEmpty()) return MSG_NON_LIE;

        try {
            List<OlympusNutritionDao.PlannedEntryRow> entries = olympusDao.weeklyPlan(oid.get());
            List<OlympusNutritionDao.MealPresetRow> presets = olympusDao.mealPresets(oid.get());

            StringBuilder res = new StringBuilder("Planning hebdomadaire des repas :\n");
            if (entries.isEmpty()) {
                res.append("Aucun repas planifié pour le moment.\n");
            } else {
                String[] jours = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
                String[] joursFr = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
                for (int i = 0; i < jours.length; i++) {
                    StringBuilder ligne = new StringBuilder();
                    for (OlympusNutritionDao.PlannedEntryRow e : entries) {
                        if (!jours[i].equalsIgnoreCase(e.dayOfWeek())) continue;
                        if (ligne.length() > 0) ligne.append(", ");
                        ligne.append(nomEntreePlanifiee(e));
                    }
                    if (ligne.length() > 0) {
                        res.append("- ").append(joursFr[i]).append(" : ").append(ligne).append("\n");
                    }
                }
            }

            res.append("\nRepas pré-enregistrés :\n");
            if (presets.isEmpty()) {
                res.append("Aucun repas pré-enregistré.");
            } else {
                for (OlympusNutritionDao.MealPresetRow p : presets) {
                    res.append("- ").append(p.name()).append(" : ")
                            .append(fmt(p.kcal())).append(" kcal, ")
                            .append(fmt(p.proteins())).append(" g prot, ")
                            .append(fmt(p.carbs())).append(" g glu, ")
                            .append(fmt(p.fats())).append(" g lip\n");
                }
            }
            return res.toString();
        } catch (DataAccessException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Récupère le détail repas par repas de ce que l'utilisateur a réellement mangé une journée donnée (format YYYY-MM-DD ; null ou vide = aujourd'hui) : chaque aliment ou repas avec sa quantité et ses calories/macros. Nécessite la liaison Olympus.")
    public String getDetailRepasJournalier(@ToolMemoryId String userId, String date) {
        Utilisateur user = loadUser(userId);
        LocalDate target;
        if (date == null || date.isBlank()) {
            target = LocalDate.now();
        } else {
            try {
                target = LocalDate.parse(date.trim());
            } catch (DateTimeParseException e) {
                return "Date invalide '" + date + "'. Utilise le format AAAA-MM-JJ ou laisse vide pour aujourd'hui.";
            }
        }

        Optional<Long> oid = nutritionService.getOlympusUserId(user.getUsername());
        if (oid.isEmpty()) return MSG_NON_LIE;

        try {
            List<OlympusNutritionDao.LogEntryRow> entries = olympusDao.logEntries(oid.get(), target);
            if (entries.isEmpty()) {
                return "Aucun aliment enregistré le " + target + ".";
            }
            StringBuilder res = new StringBuilder("Détail des repas du ").append(target).append(" :\n");
            for (OlympusNutritionDao.LogEntryRow e : entries) {
                res.append("- ").append(e.name());
                if (e.quantityGrams() != null && e.quantityGrams() > 0) {
                    res.append(" (").append(fmt(e.quantityGrams())).append(" g)");
                }
                res.append(" : ").append(fmt(e.kcal())).append(" kcal, ")
                        .append(fmt(e.proteins())).append(" g prot, ")
                        .append(fmt(e.carbs())).append(" g glu, ")
                        .append(fmt(e.fats())).append(" g lip\n");
            }
            return res.toString();
        } catch (DataAccessException ex) {
            return MSG_INDISPO;
        }
    }

    // ------------------------------------------------------------------- Helpers

    private String nomEntreePlanifiee(OlympusNutritionDao.PlannedEntryRow e) {
        if (e.foodName() != null) {
            return e.quantityGrams() != null
                    ? e.foodName() + " (" + fmt(e.quantityGrams()) + " g)" : e.foodName();
        }
        if (e.presetName() != null) return e.presetName();
        return "Repas";
    }

    private String goalFr(String goal) {
        if (goal == null) return "non défini";
        return switch (goal.toUpperCase()) {
            case "LOSE_WEIGHT" -> "perte de poids";
            case "MAINTAIN" -> "maintien";
            case "GAIN_MUSCLE" -> "prise de muscle";
            default -> goal;
        };
    }

    private String activityFr(String activity) {
        if (activity == null) return "non défini";
        return switch (activity.toUpperCase()) {
            case "SEDENTARY" -> "sédentaire";
            case "LIGHT" -> "léger";
            case "MODERATE" -> "modéré";
            case "INTENSE" -> "intense";
            default -> activity;
        };
    }

    private String fmtKg(double kg) {
        return String.format(java.util.Locale.FRANCE, "%.1f kg", kg);
    }

    private String formatDeltaKg(double delta) {
        if (Math.abs(delta) < 0.05) return "0 kg";
        String sign = delta > 0 ? "+" : "";
        return sign + String.format(java.util.Locale.FRANCE, "%.1f kg", delta);
    }

    private Utilisateur loadUser(String userId) {
        return utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
    }

    private double orZero(Double v) {
        return v == null ? 0.0 : v;
    }

    private String fmt(double v) {
        return String.format(java.util.Locale.FRANCE, "%.0f", v);
    }

    private String formatDelta(double delta, String unit) {
        if (Math.abs(delta) < 0.5) return "à la cible";
        String sign = delta > 0 ? "+" : "";
        return sign + String.format(java.util.Locale.FRANCE, "%.0f", delta) + " " + unit;
    }
}
