package com.kronos.chiron.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.nutrition.NutritionService;
import com.kronos.chiron.nutrition.OlympusClient;
import com.kronos.chiron.repository.UtilisateurRepository;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Outils LangChain4j donnant à Chiron accès aux données nutritionnelles
 * de l'utilisateur via son compte Olympus lié.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NutritionTools {

    private static final String MSG_NON_LIE =
            "L'utilisateur n'a pas lié son compte Olympus à Chiron. Demande-lui de le faire depuis la page Profil.";
    private static final String MSG_EXPIRE =
            "La liaison Olympus de l'utilisateur a expiré. Demande-lui de se relier depuis la page Profil.";
    private static final String MSG_INDISPO =
            "Le service Olympus est temporairement injoignable. Réessaie plus tard.";

    private final UtilisateurRepository utilisateurRepository;
    private final NutritionService nutritionService;
    private final OlympusClient olympusClient;

    @Tool("Récupère l'apport nutritionnel de l'utilisateur pour une date donnée (format YYYY-MM-DD ; null ou vide = aujourd'hui). Retourne calories, protéines, glucides, lipides consommés + cibles + delta + activité du jour. Nécessite que l'utilisateur ait lié son compte Olympus.")
    public String getApportJournalier(@MemoryId String userId, String date) {
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

        try {
            String token = nutritionService.getValidToken(user.getUsername());
            JsonNode log = olympusClient.getDailyLog(token, target);
            JsonNode profile = olympusClient.getUserProfile(token);

            double consoKcal = asDouble(log, "totalKcal");
            double consoProt = asDouble(log, "totalProteins");
            double consoGlu  = asDouble(log, "totalCarbs");
            double consoLip  = asDouble(log, "totalFats");
            Integer pas = log.hasNonNull("stepCount") ? log.get("stepCount").asInt() : null;
            Integer dureeMin = log.hasNonNull("workoutDurationMinutes") ? log.get("workoutDurationMinutes").asInt() : null;
            double extraBrul = asDouble(log, "extraKcalBurned");

            double cibleKcal = asDouble(profile, "targetKcal");
            double cibleProt = asDouble(profile, "targetProteins");
            double cibleGlu  = asDouble(profile, "targetCarbs");
            double cibleLip  = asDouble(profile, "targetFats");
            String goal = profile.hasNonNull("goal") ? profile.get("goal").asText() : null;

            StringBuilder res = new StringBuilder();
            res.append("Apport du ").append(target).append(" :\n");
            res.append("- Calories : ").append(fmt(consoKcal)).append(" / ").append(fmt(cibleKcal))
                    .append(" kcal (").append(formatDelta(consoKcal - cibleKcal, "kcal")).append(")\n");
            res.append("- Protéines : ").append(fmt(consoProt)).append(" / ").append(fmt(cibleProt)).append(" g\n");
            res.append("- Glucides : ").append(fmt(consoGlu)).append(" / ").append(fmt(cibleGlu)).append(" g\n");
            res.append("- Lipides : ").append(fmt(consoLip)).append(" / ").append(fmt(cibleLip)).append(" g\n");
            if (goal != null) {
                res.append("Objectif déclaré : ").append(goal).append("\n");
            }
            if (pas != null) {
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
        } catch (NutritionService.NotLinkedException e) {
            return MSG_NON_LIE;
        } catch (NutritionService.ExpiredException e) {
            return MSG_EXPIRE;
        } catch (OlympusClient.OlympusUnauthorizedException e) {
            nutritionService.invalidateLink(user.getUsername());
            return MSG_EXPIRE;
        } catch (OlympusClient.OlympusUnavailableException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Récupère les objectifs nutritionnels de l'utilisateur : type d'objectif (perte / maintien / prise), cibles calories et macros, poids actuel. Nécessite la liaison Olympus.")
    public String getObjectifsNutritionnels(@MemoryId String userId) {
        Utilisateur user = loadUser(userId);
        try {
            String token = nutritionService.getValidToken(user.getUsername());
            JsonNode profile = olympusClient.getUserProfile(token);

            String goal = profile.hasNonNull("goal") ? profile.get("goal").asText() : "non défini";
            String activity = profile.hasNonNull("activityLevel") ? profile.get("activityLevel").asText() : null;
            double poids = asDouble(profile, "currentWeightKg");
            double taille = asDouble(profile, "heightCm");
            double cibleKcal = asDouble(profile, "targetKcal");
            double cibleProt = asDouble(profile, "targetProteins");
            double cibleGlu  = asDouble(profile, "targetCarbs");
            double cibleLip  = asDouble(profile, "targetFats");

            StringBuilder res = new StringBuilder("Profil nutritionnel :\n");
            res.append("- Objectif : ").append(goal).append("\n");
            if (poids > 0) res.append("- Poids actuel : ").append(fmt(poids)).append(" kg\n");
            if (taille > 0) res.append("- Taille : ").append(fmt(taille)).append(" cm\n");
            if (activity != null) res.append("- Niveau d'activité : ").append(activity).append("\n");
            res.append("Cibles journalières :\n");
            res.append("- Calories : ").append(fmt(cibleKcal)).append(" kcal\n");
            res.append("- Protéines : ").append(fmt(cibleProt)).append(" g\n");
            res.append("- Glucides : ").append(fmt(cibleGlu)).append(" g\n");
            res.append("- Lipides : ").append(fmt(cibleLip)).append(" g");
            return res.toString();
        } catch (NutritionService.NotLinkedException e) {
            return MSG_NON_LIE;
        } catch (NutritionService.ExpiredException e) {
            return MSG_EXPIRE;
        } catch (OlympusClient.OlympusUnauthorizedException e) {
            nutritionService.invalidateLink(user.getUsername());
            return MSG_EXPIRE;
        } catch (OlympusClient.OlympusUnavailableException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Analyse l'équilibre nutritionnel de l'utilisateur sur les N derniers jours (défaut 7) : moyenne calorique vs cible, répartition moyenne en macros, écart à l'objectif. Détecte les déficits/surplus marqués. Nécessite la liaison Olympus.")
    public String analyserEquilibreMacros(@MemoryId String userId, Integer nbJours) {
        Utilisateur user = loadUser(userId);
        int window = (nbJours != null && nbJours > 0) ? nbJours : 7;

        try {
            String token = nutritionService.getValidToken(user.getUsername());
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(window - 1L);
            JsonNode analytics = olympusClient.getAnalytics(token, start, end);

            double avgKcal = asDouble(analytics, "averageKcal");
            double avgWeight = asDouble(analytics, "averageWeight");
            double fatLossG = asDouble(analytics, "estimatedFatLossGrams");

            JsonNode daily = analytics.get("dailyData");
            int joursAvecDonnees = 0;
            double sumProt = 0, sumGlu = 0, sumLip = 0;
            double sumCibleKcal = 0;
            int joursAvecCible = 0;
            if (daily != null && daily.isArray()) {
                for (JsonNode pt : daily) {
                    boolean hasIntake = pt.hasNonNull("totalKcal");
                    if (hasIntake) {
                        sumProt += asDouble(pt, "totalProteins");
                        sumGlu  += asDouble(pt, "totalCarbs");
                        sumLip  += asDouble(pt, "totalFats");
                        joursAvecDonnees++;
                    }
                    if (pt.hasNonNull("targetKcal")) {
                        sumCibleKcal += pt.get("targetKcal").asDouble();
                        joursAvecCible++;
                    }
                }
            }

            if (joursAvecDonnees == 0) {
                return "Aucun apport enregistré sur les " + window + " derniers jours.";
            }

            double avgProt = sumProt / joursAvecDonnees;
            double avgGlu  = sumGlu  / joursAvecDonnees;
            double avgLip  = sumLip  / joursAvecDonnees;
            double totalGrammes = avgProt + avgGlu + avgLip;
            double pctProt = totalGrammes > 0 ? (avgProt * 4.0) / (avgProt * 4 + avgGlu * 4 + avgLip * 9) * 100 : 0;
            double pctGlu  = totalGrammes > 0 ? (avgGlu  * 4.0) / (avgProt * 4 + avgGlu * 4 + avgLip * 9) * 100 : 0;
            double pctLip  = totalGrammes > 0 ? (avgLip  * 9.0) / (avgProt * 4 + avgGlu * 4 + avgLip * 9) * 100 : 0;

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
                        .append(String.format(java.util.Locale.FRANCE, "%.0f", ecartPct))
                        .append(" %)");
            }
            res.append("\n");
            res.append("- Répartition moyenne : ")
                    .append(String.format(java.util.Locale.FRANCE, "%.0f", pctProt)).append(" % prot / ")
                    .append(String.format(java.util.Locale.FRANCE, "%.0f", pctGlu)).append(" % glu / ")
                    .append(String.format(java.util.Locale.FRANCE, "%.0f", pctLip)).append(" % lip\n");
            res.append("- Macros moyens : ").append(fmt(avgProt)).append(" g prot, ")
                    .append(fmt(avgGlu)).append(" g glu, ").append(fmt(avgLip)).append(" g lip / jour\n");
            if (avgWeight > 0) res.append("- Poids moyen : ").append(fmt(avgWeight)).append(" kg\n");
            if (Math.abs(fatLossG) > 0.1) {
                res.append("- Perte de graisse estimée sur la période : ").append(fmt(fatLossG)).append(" g");
            }
            return res.toString();
        } catch (NutritionService.NotLinkedException e) {
            return MSG_NON_LIE;
        } catch (NutritionService.ExpiredException e) {
            return MSG_EXPIRE;
        } catch (OlympusClient.OlympusUnauthorizedException e) {
            nutritionService.invalidateLink(user.getUsername());
            return MSG_EXPIRE;
        } catch (OlympusClient.OlympusUnavailableException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Récupère l'évolution du poids de l'utilisateur sur les N derniers jours (défaut 30) : première et dernière mesure, tendance (perte / prise / stable). Nécessite la liaison Olympus et que l'utilisateur se pèse régulièrement dans Olympus.")
    public String getEvolutionPoids(@MemoryId String userId, Integer nbJours) {
        Utilisateur user = loadUser(userId);
        int window = (nbJours != null && nbJours > 0) ? nbJours : 30;

        try {
            String token = nutritionService.getValidToken(user.getUsername());
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(window - 1L);
            JsonNode analytics = olympusClient.getAnalytics(token, start, end);

            JsonNode daily = analytics.get("dailyData");
            if (daily == null || !daily.isArray() || daily.isEmpty()) {
                return "Aucune mesure de poids enregistrée sur les " + window + " derniers jours.";
            }

            String premiereDate = null, derniereDate = null;
            double premierPoids = 0, dernierPoids = 0;
            int nbMesures = 0;
            double sommePoids = 0;
            double poidsMin = Double.MAX_VALUE, poidsMax = -Double.MAX_VALUE;

            for (JsonNode pt : daily) {
                if (!pt.hasNonNull("weightKg")) continue;
                double w = pt.get("weightKg").asDouble();
                String d = pt.hasNonNull("date") ? pt.get("date").asText() : null;
                if (premiereDate == null) { premiereDate = d; premierPoids = w; }
                derniereDate = d;
                dernierPoids = w;
                sommePoids += w;
                nbMesures++;
                if (w < poidsMin) poidsMin = w;
                if (w > poidsMax) poidsMax = w;
            }

            if (nbMesures == 0) {
                return "Aucune mesure de poids enregistrée sur les " + window + " derniers jours.";
            }

            double delta = dernierPoids - premierPoids;
            String tendance;
            if (Math.abs(delta) < 0.3) tendance = "stable";
            else if (delta < 0) tendance = "perte";
            else tendance = "prise";

            StringBuilder res = new StringBuilder();
            res.append("Évolution du poids sur ").append(window).append(" jours (")
                    .append(nbMesures).append(" mesure(s)) :\n");
            res.append("- Première mesure : ").append(fmtKg(premierPoids))
                    .append(premiereDate != null ? " (le " + premiereDate + ")" : "").append("\n");
            res.append("- Dernière mesure : ").append(fmtKg(dernierPoids))
                    .append(derniereDate != null ? " (le " + derniereDate + ")" : "").append("\n");
            res.append("- Variation : ").append(formatDeltaKg(delta)).append(" (").append(tendance).append(")\n");
            if (nbMesures >= 3) {
                res.append("- Moyenne période : ").append(fmtKg(sommePoids / nbMesures)).append("\n");
                res.append("- Min/Max : ").append(fmtKg(poidsMin)).append(" / ").append(fmtKg(poidsMax));
            }
            return res.toString();
        } catch (NutritionService.NotLinkedException e) {
            return MSG_NON_LIE;
        } catch (NutritionService.ExpiredException e) {
            return MSG_EXPIRE;
        } catch (OlympusClient.OlympusUnauthorizedException e) {
            nutritionService.invalidateLink(user.getUsername());
            return MSG_EXPIRE;
        } catch (OlympusClient.OlympusUnavailableException e) {
            return MSG_INDISPO;
        }
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

    private double asDouble(JsonNode node, String field) {
        return (node != null && node.hasNonNull(field)) ? node.get(field).asDouble() : 0.0;
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
