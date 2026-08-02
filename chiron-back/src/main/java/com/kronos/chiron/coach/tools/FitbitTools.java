package com.kronos.chiron.coach.tools;

import java.time.Clock;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.fitbit.FitbitClient;
import com.kronos.chiron.fitbit.FitbitParser;
import com.kronos.chiron.fitbit.FitbitService;
import com.kronos.chiron.fitbit.FitbitSyncService;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FitbitTools {

    private static final String MSG_NON_LIE =
            "L'utilisateur n'a pas lié son compte Fitbit à Chiron. Demande-lui de le faire depuis la page Profil.";
    private static final String MSG_EXPIRE =
            "La liaison Fitbit de l'utilisateur a expiré. Demande-lui de se reconnecter depuis la page Profil.";
    private static final String MSG_INDISPO =
            "Le service Fitbit est temporairement injoignable. Réessaie plus tard.";

    private final UtilisateurRepository utilisateurRepository;
    private final FitbitService fitbitService;
    private final FitbitClient fitbitClient;
    private final FitbitSyncService fitbitSyncService;

    private final Clock clock;
    @Tool("Récupère l'activité physique (nombre de pas) de l'utilisateur pour une date donnée (format YYYY-MM-DD ; null ou vide = aujourd'hui), d'après Fitbit. Nécessite que l'utilisateur ait lié son compte Fitbit.")
    public String getActiviteJournaliere(@ToolMemoryId String userId, String date) {
        Utilisateur user = loadUser(userId);
        LocalDate target = parseDateOrToday(date);
        if (target == null) {
            return "Date invalide '" + date + "'. Utilise le format AAAA-MM-JJ ou laisse vide pour aujourd'hui.";
        }
        try {
            String token = fitbitService.getValidToken(user.getUsername());
            Map<LocalDate, Integer> steps = FitbitParser.stepsByDate(
                    fitbitClient.rollUpDailySteps(token, target, target));
            Integer pas = steps.get(target);
            triggerSync(user);
            if (pas == null) {
                return "Aucune donnée d'activité Fitbit pour le " + target + ".";
            }
            return "Activité Fitbit du " + target + " : " + pas + " pas.";
        } catch (FitbitService.NotLinkedException e) {
            return MSG_NON_LIE;
        } catch (FitbitService.ExpiredException e) {
            return MSG_EXPIRE;
        } catch (FitbitClient.FitbitUnauthorizedException e) {
            log.warn("FITBIT_TOOL_UNAUTHORIZED user={} : {}", user.getUsername(), e.getMessage());
            return MSG_INDISPO;
        } catch (FitbitClient.FitbitUnavailableException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Récupère le sommeil de l'utilisateur sur les N dernières nuits (défaut 7) d'après Fitbit : heures de sommeil par nuit, moyenne, dernière nuit. Nécessite la liaison Fitbit.")
    public String getSommeilRecent(@ToolMemoryId String userId, Integer nbJours) {
        Utilisateur user = loadUser(userId);
        int window = (nbJours != null && nbJours > 0) ? Math.min(nbJours, 30) : 7;
        try {
            String token = fitbitService.getValidToken(user.getUsername());
            LocalDate today = LocalDate.now(clock);
            LocalDate start = today.minusDays(window - 1L);
            Map<LocalDate, Double> hoursByDate =
                    FitbitParser.sleepHoursByDate(fitbitClient.listSleep(token, start));

            if (hoursByDate.isEmpty()) {
                triggerSync(user);
                return "Aucun sommeil enregistré sur Fitbit pour les " + window + " dernières nuits.";
            }
            double somme = 0;
            int n = 0;
            StringBuilder lignes = new StringBuilder();
            for (int i = window - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                Double h = hoursByDate.get(d);
                if (h != null) {
                    lignes.append("- ").append(d).append(" : ").append(fmt1(h)).append(" h\n");
                    somme += h;
                    n++;
                }
            }
            StringBuilder res = new StringBuilder("Sommeil Fitbit sur ").append(window)
                    .append(" nuits (").append(n).append(" nuit(s) avec données) :\n");
            res.append(lignes);
            res.append("- Moyenne : ").append(fmt1(somme / n)).append(" h/nuit");
            Double derniere = hoursByDate.get(today);
            if (derniere != null) {
                res.append("\n- Dernière nuit : ").append(fmt1(derniere)).append(" h");
            }
            triggerSync(user);
            return res.toString();
        } catch (FitbitService.NotLinkedException e) {
            return MSG_NON_LIE;
        } catch (FitbitService.ExpiredException e) {
            return MSG_EXPIRE;
        } catch (FitbitClient.FitbitUnauthorizedException e) {
            log.warn("FITBIT_TOOL_UNAUTHORIZED user={} : {}", user.getUsername(), e.getMessage());
            return MSG_INDISPO;
        } catch (FitbitClient.FitbitUnavailableException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Récupère la fréquence cardiaque de repos de l'utilisateur d'après Fitbit, pour une date donnée (format YYYY-MM-DD ; null ou vide = aujourd'hui) ou, à défaut, la mesure la plus récente. Nécessite la liaison Fitbit.")
    public String getFrequenceCardiaque(@ToolMemoryId String userId, String date) {
        Utilisateur user = loadUser(userId);
        LocalDate target = parseDateOrToday(date);
        if (target == null) {
            return "Date invalide '" + date + "'. Utilise le format AAAA-MM-JJ ou laisse vide pour aujourd'hui.";
        }
        try {
            String token = fitbitService.getValidToken(user.getUsername());
            Map<LocalDate, Integer> hrByDate = FitbitParser.restingHeartRateByDate(
                    fitbitClient.listRestingHeartRate(token, target.minusDays(6)));
            triggerSync(user);

            Integer exact = hrByDate.get(target);
            if (exact != null) {
                return "Fréquence cardiaque de repos Fitbit du " + target + " : " + exact + " bpm.";
            }
            Map.Entry<LocalDate, Integer> recent = hrByDate.entrySet().stream()
                    .max(Map.Entry.comparingByKey()).orElse(null);
            if (recent == null) {
                return "Aucune fréquence cardiaque de repos Fitbit disponible autour du " + target + ".";
            }
            return "Pas de FC de repos Fitbit pour le " + target + ". Mesure la plus récente : "
                    + recent.getValue() + " bpm le " + recent.getKey() + ".";
        } catch (FitbitService.NotLinkedException e) {
            return MSG_NON_LIE;
        } catch (FitbitService.ExpiredException e) {
            return MSG_EXPIRE;
        } catch (FitbitClient.FitbitUnauthorizedException e) {
            log.warn("FITBIT_TOOL_UNAUTHORIZED user={} : {}", user.getUsername(), e.getMessage());
            return MSG_INDISPO;
        } catch (FitbitClient.FitbitUnavailableException e) {
            return MSG_INDISPO;
        }
    }

    @Tool("Analyse la tendance d'activité de l'utilisateur sur les N derniers jours (défaut 7) d'après Fitbit : pas par jour, moyenne, minimum, maximum, tendance. Nécessite la liaison Fitbit.")
    public String getTendanceActivite(@ToolMemoryId String userId, Integer nbJours) {
        Utilisateur user = loadUser(userId);
        int window = (nbJours != null && nbJours > 0) ? Math.min(nbJours, 30) : 7;
        try {
            String token = fitbitService.getValidToken(user.getUsername());
            LocalDate today = LocalDate.now(clock);
            LocalDate start = today.minusDays(window - 1L);
            Map<LocalDate, Integer> stepsByDate = FitbitParser.stepsByDate(
                    fitbitClient.rollUpDailySteps(token, start, today));
            if (stepsByDate.isEmpty()) {
                triggerSync(user);
                return "Aucune donnée de pas Fitbit sur les " + window + " derniers jours.";
            }
            long somme = 0;
            int n = 0, min = Integer.MAX_VALUE, max = 0, premier = -1, dernier = 0;
            StringBuilder lignes = new StringBuilder();
            for (int i = window - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                Integer steps = stepsByDate.get(d);
                if (steps == null) continue;
                lignes.append("- ").append(d).append(" : ").append(steps).append(" pas\n");
                somme += steps;
                n++;
                if (steps < min) min = steps;
                if (steps > max) max = steps;
                if (premier < 0) premier = steps;
                dernier = steps;
            }
            if (n == 0) {
                triggerSync(user);
                return "Aucune donnée de pas Fitbit exploitable sur les " + window + " derniers jours.";
            }
            long moyenne = somme / n;
            String tendance;
            if (premier == 0 || Math.abs(dernier - premier) < moyenne * 0.1) tendance = "stable";
            else if (dernier > premier) tendance = "en hausse";
            else tendance = "en baisse";

            StringBuilder res = new StringBuilder("Tendance d'activité Fitbit sur ")
                    .append(window).append(" jours (").append(n).append(" jour(s) avec données) :\n");
            res.append(lignes);
            res.append("- Moyenne : ").append(moyenne).append(" pas/jour\n");
            res.append("- Min/Max : ").append(min).append(" / ").append(max).append(" pas\n");
            res.append("- Tendance : ").append(tendance);
            triggerSync(user);
            return res.toString();
        } catch (FitbitService.NotLinkedException e) {
            return MSG_NON_LIE;
        } catch (FitbitService.ExpiredException e) {
            return MSG_EXPIRE;
        } catch (FitbitClient.FitbitUnauthorizedException e) {
            log.warn("FITBIT_TOOL_UNAUTHORIZED user={} : {}", user.getUsername(), e.getMessage());
            return MSG_INDISPO;
        } catch (FitbitClient.FitbitUnavailableException e) {
            return MSG_INDISPO;
        }
    }

    private void triggerSync(Utilisateur user) {
        try {
            fitbitSyncService.syncEtatJournalier(user.getUsername(), 3);
        } catch (RuntimeException e) {
            log.debug("FITBIT_TOOL_SYNC_SKIPPED user={} : {}", user.getUsername(), e.getMessage());
        }
    }

    private LocalDate parseDateOrToday(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(clock);
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Utilisateur loadUser(String userId) {
        return utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
    }

    private String fmt1(double v) {
        return String.format(Locale.FRANCE, "%.1f", v);
    }
}
