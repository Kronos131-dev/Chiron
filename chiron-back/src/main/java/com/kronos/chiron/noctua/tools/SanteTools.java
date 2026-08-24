package com.kronos.chiron.noctua.tools;

import com.kronos.chiron.coach.tools.ToolUserResolver;
import com.kronos.chiron.sante.dto.SanteCardioHebdoDto;
import com.kronos.chiron.sante.dto.SanteResumeDto;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.sante.model.SanteSyncState;
import com.kronos.chiron.sante.model.StatutSync;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.sante.persistence.SanteSyncStateRepository;
import com.kronos.chiron.sante.service.SanteQueryService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SanteTools {

    private static final int HISTORIQUE_JOURS_DEFAUT = 30;
    private static final int HISTORIQUE_ACTIVITES_JOURS = 90;
    private static final int MIN_ACTIVITES_COMPARABLES = 3;

    private final SanteQueryService santeQueryService;
    private final SanteJourRepository santeJourRepository;
    private final SanteSommeilRepository santeSommeilRepository;
    private final SanteActiviteRepository santeActiviteRepository;
    private final SanteSyncStateRepository santeSyncStateRepository;
    private final ToolUserResolver toolUserResolver;
    private final Clock clock;

    @Tool("Récupère le résumé du jour : pas, distance, calories, minutes actives, FC de repos, score de sommeil, score de préparation. Date optionnelle (AAAA-MM-JJ ; null ou vide = aujourd'hui).")
    public String getResumeDuJour(@ToolMemoryId String memoryId, String date) {
        Utilisateur user = toolUserResolver.load(memoryId);
        LocalDate target = parseDateOuAujourdhui(date);
        if (target.equals(LocalDate.now(clock))) {
            SanteResumeDto r = santeQueryService.getResume(user);
            if (!r.linked()) return "Le compte n'est pas lié à Google Health.";
            if (r.needsReconnect()) return "La liaison Google Health a expiré, une reconnexion est nécessaire.";
            return "Le " + r.date() + " : " + pas(r.pas()) + ", " + distance(r.distanceM()) + ", "
                    + calories(r.caloriesTotales()) + ", " + minutesActives(r.minutesZoneActive()) + ", "
                    + fcRepos(r.fcRepos()) + ", " + scoreSommeil(r.scoreSommeil()) + ", "
                    + scorePreparation(r.scorePreparation()) + ".";
        }
        Optional<SanteJour> jourOpt = santeJourRepository.findByUtilisateurAndDate(user, target);
        if (jourOpt.isEmpty()) return "Aucune donnée santé pour le " + target + ".";
        SanteJour jour = jourOpt.get();
        return "Le " + target + " : " + pas(jour.getPas()) + ", "
                + distance(jour.getDistanceM()) + ", " + calories(jour.getCaloriesTotales()) + ", "
                + minutesActives(jour.getMinutesZoneActive()) + ", " + fcRepos(jour.getFcRepos()) + ".";
    }

    @Tool("Récupère la nuit principale (hors sieste) d'une date : durée, stades de sommeil, réveils, FC de sommeil, score et sous-scores. Date optionnelle (AAAA-MM-JJ ; null ou vide = aujourd'hui).")
    public String getNuit(@ToolMemoryId String memoryId, String date) {
        Utilisateur user = toolUserResolver.load(memoryId);
        LocalDate target = parseDateOuAujourdhui(date);
        Optional<SanteSommeil> nuitOpt = santeSommeilRepository
                .findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user, target);
        if (nuitOpt.isEmpty()) return "Aucune nuit enregistrée pour le " + target + ".";
        SanteSommeil n = nuitOpt.get();
        StringBuilder res = new StringBuilder("Nuit du " + target + " : ");
        res.append(n.getMinutesEndormi() != null
                ? formatMinutesEnHeures(n.getMinutesEndormi()) + " de sommeil"
                : "durée inconnue");
        if (n.isStadesDisponibles()) {
            res.append(", profond ").append(minutesOuInconnu(n.getMinutesProfond()))
                    .append(", léger ").append(minutesOuInconnu(n.getMinutesLeger()))
                    .append(", paradoxal ").append(minutesOuInconnu(n.getMinutesParadoxal()));
        }
        if (n.getNbReveils() != null) res.append(", ").append(n.getNbReveils()).append(" réveil(s)");
        if (n.getFcSommeilMoyenne() != null) {
            res.append(", FC moyenne ").append(Math.round(n.getFcSommeilMoyenne())).append(" bpm");
        }
        if (n.getScore() != null) {
            res.append(". Score de sommeil ").append(n.getScore()).append("/100");
            if (n.getScoreDuree() != null || n.getScoreComposition() != null || n.getScoreRestauration() != null) {
                res.append(" (durée ").append(sousScore(n.getScoreDuree()))
                        .append(", composition ").append(sousScore(n.getScoreComposition()))
                        .append(", restauration ").append(sousScore(n.getScoreRestauration())).append(")");
            }
        }
        res.append(".");
        return res.toString();
    }

    @Tool("Récupère la dernière activité physique terminée (musculation Chiron ou détectée par Google) : type, durée, FC, minutes par zone, charge cardio.")
    public String getDerniereActivite(@ToolMemoryId String memoryId) {
        Utilisateur user = toolUserResolver.load(memoryId);
        Optional<SanteActivite> activiteOpt = santeActiviteRepository.findFirstByUtilisateurOrderByEndTimeDesc(user);
        if (activiteOpt.isEmpty()) return "Aucune activité enregistrée.";
        return decrireActivite(activiteOpt.get());
    }

    @Tool("Récupère le détail d'une activité physique par son identifiant numérique : type, durée, FC, minutes par zone, charge cardio.")
    public String getActivite(@ToolMemoryId String memoryId, Long id) {
        if (id == null) return "Identifiant d'activité manquant.";
        Utilisateur user = toolUserResolver.load(memoryId);
        Optional<SanteActivite> activiteOpt = santeActiviteRepository.findById(id);
        if (activiteOpt.isEmpty() || !activiteOpt.get().getUtilisateur().getId().equals(user.getId())) {
            return "Aucune activité #" + id + " trouvée pour cet utilisateur.";
        }
        return decrireActivite(activiteOpt.get());
    }

    @Tool("Compare la FC de repos, la VFC, la charge cardio et le score de sommeil du jour à leur médiane sur les N derniers jours (défaut 30), pour repérer une dérive.")
    public String getTendanceSante(@ToolMemoryId String memoryId, Integer nbJours) {
        Utilisateur user = toolUserResolver.load(memoryId);
        int window = (nbJours != null && nbJours > 0) ? nbJours : HISTORIQUE_JOURS_DEFAUT;
        LocalDate today = LocalDate.now(clock);
        List<SanteJour> historique = santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(user,
                today.minusDays(window - 1L), today);
        if (historique.isEmpty()) return "Aucune donnée santé sur les " + window + " derniers jours.";

        Optional<SanteJour> jourDuJourOpt = historique.stream()
                .filter(j -> j.getDate().equals(today)).findFirst();
        if (jourDuJourOpt.isEmpty()) return "Aucune donnée santé pour aujourd'hui.";
        SanteJour jour = jourDuJourOpt.get();

        StringBuilder res = new StringBuilder("Sur les ").append(window).append(" derniers jours : ");
        res.append(comparerAMediane("FC de repos", jour.getFcRepos() == null ? null : jour.getFcRepos().doubleValue(),
                historique, j -> j.getFcRepos() == null ? null : j.getFcRepos().doubleValue(), "bpm"));
        res.append(comparerAMediane("VFC", jour.getVfcMs(), historique, SanteJour::getVfcMs, "ms"));
        res.append(comparerAMediane("charge cardio", jour.getChargeCardio(), historique, SanteJour::getChargeCardio,
                ""));
        return res.toString();
    }

    @Tool("Récupère la charge cardio de la semaine en cours et la compare à la cible basse/haute.")
    public String getChargeCardioHebdo(@ToolMemoryId String memoryId) {
        Utilisateur user = toolUserResolver.load(memoryId);
        List<SanteCardioHebdoDto> semaines = santeQueryService.getCardioHebdo(user, 1);
        if (semaines.isEmpty()) return "Aucune charge cardio calculée pour la semaine en cours.";
        SanteCardioHebdoDto s = semaines.get(semaines.size() - 1);
        if (s.chargeCardio() == null) return "Aucune charge cardio calculée pour la semaine en cours.";
        StringBuilder res = new StringBuilder("Charge cardio de la semaine : ")
                .append(Math.round(s.chargeCardio()));
        if (s.cibleBasse() != null && s.cibleHaute() != null) {
            res.append(" (cible ").append(Math.round(s.cibleBasse())).append("-").append(Math.round(s.cibleHaute()))
                    .append(")");
            if (s.chargeCardio() > s.cibleHaute()) res.append(", au-dessus de la cible");
            else if (s.chargeCardio() < s.cibleBasse()) res.append(", en-dessous de la cible");
        }
        res.append(".");
        return res.toString();
    }

    @Tool("Récupère le détail de la fréquence cardiaque d'une journée : minimum, moyenne, maximum et minutes passées dans chaque zone. Date optionnelle (AAAA-MM-JJ ; null ou vide = aujourd'hui).")
    public String getFrequenceCardiaqueDuJour(@ToolMemoryId String memoryId, String date) {
        Utilisateur user = toolUserResolver.load(memoryId);
        LocalDate target = parseDateOuAujourdhui(date);
        Optional<SanteJour> jourOpt = santeJourRepository.findByUtilisateurAndDate(user, target);
        if (jourOpt.isEmpty()) return "Aucune donnée de fréquence cardiaque pour le " + target + ".";
        SanteJour jour = jourOpt.get();
        if (jour.getFcMin() == null && jour.getFcMax() == null && jour.getFcMoyenne() == null) {
            return "Aucune donnée de fréquence cardiaque pour le " + target + ".";
        }
        StringBuilder res = new StringBuilder("FC du " + target + " : ");
        res.append(minMaxMoyenne(jour.getFcMin(), jour.getFcMoyenne(), jour.getFcMax()));
        res.append(", zones : brûle-graisse ").append(minutesOuInconnu(jour.getMinutesZoneBruleuse()))
                .append(", cardio ").append(minutesOuInconnu(jour.getMinutesZoneCardio()))
                .append(", pic ").append(minutesOuInconnu(jour.getMinutesZonePic())).append(".");
        return res.toString();
    }

    @Tool("Liste les types de données Google Health qui n'ont pas pu être synchronisés récemment, pour ne pas conclure sur une absence de donnée comme si elle était réelle.")
    public String getEtatSync(@ToolMemoryId String memoryId) {
        Utilisateur user = toolUserResolver.load(memoryId);
        List<SanteSyncState> etats = santeSyncStateRepository.findByUtilisateur(user);
        List<SanteSyncState> enEchec = etats.stream()
                .filter(e -> e.getDernierStatut() != StatutSync.OK && e.getDernierStatut() != StatutSync.VIDE)
                .toList();
        List<SanteSyncState> vides = etats.stream().filter(e -> e.getDernierStatut() == StatutSync.VIDE).toList();
        if (enEchec.isEmpty() && vides.isEmpty()) return "Toutes les données Google Health sont à jour.";

        StringBuilder res = new StringBuilder();
        if (!enEchec.isEmpty()) {
            res.append("Données non à jour : ");
            for (SanteSyncState e : enEchec) {
                res.append(e.getTypeDonnee()).append(" (").append(e.getDernierStatut()).append(") ");
            }
        }
        if (!vides.isEmpty()) {
            res.append("Aucune donnée nouvelle depuis la dernière synchro pour : ");
            for (SanteSyncState e : vides) {
                res.append(e.getTypeDonnee()).append(" ");
            }
        }
        return res.toString().trim() + ".";
    }

    private String decrireActivite(SanteActivite a) {
        StringBuilder res = new StringBuilder("Activité #" + a.getId() + " (" + a.getTypeActivite() + ") du "
                + a.getStartTime().toLocalDate() + " : ");
        long minutes = java.time.Duration.between(a.getStartTime(), a.getEndTime()).toMinutes();
        res.append(minutes).append(" min");
        if (a.getFcMoyenne() != null) res.append(", FC moyenne ").append(Math.round(a.getFcMoyenne())).append(" bpm");
        if (a.getFcMax() != null) res.append(", FC max ").append(a.getFcMax()).append(" bpm");
        if (a.getCalories() != null) res.append(", ").append(a.getCalories()).append(" kcal");
        if (a.getMinutesZoneBruleuse() != null || a.getMinutesZoneCardio() != null || a.getMinutesZonePic() != null) {
            res.append(", zones brûle-graisse ").append(minutesOuInconnu(a.getMinutesZoneBruleuse()))
                    .append("/cardio ").append(minutesOuInconnu(a.getMinutesZoneCardio()))
                    .append("/pic ").append(minutesOuInconnu(a.getMinutesZonePic()));
        }
        if (a.getChargeCardio() != null) res.append(", charge cardio ").append(Math.round(a.getChargeCardio()));
        res.append(". ").append(comparerActiviteAMediane(a));
        return res.toString().trim();
    }

    // WHY: le prompt de Noctua exige une comparaison avant toute interprétation ; sans
    // cette clause le modèle n'a que les chiffres de la séance elle-même et inventerait
    // une base de comparaison.
    private String comparerActiviteAMediane(SanteActivite a) {
        if (a.getChargeCardio() == null) return "";
        LocalDate depuis = LocalDate.now(clock).minusDays(HISTORIQUE_ACTIVITES_JOURS);
        List<SanteActivite> historique = santeActiviteRepository
                .findByUtilisateurAndTypeActiviteAndStartTimeAfterOrderByStartTimeDesc(a.getUtilisateur(),
                        a.getTypeActivite(), depuis.atStartOfDay())
                .stream()
                .filter(autre -> !autre.getId().equals(a.getId()) && autre.getChargeCardio() != null)
                .toList();
        if (historique.size() < MIN_ACTIVITES_COMPARABLES) return "";

        List<Double> charges = historique.stream().map(SanteActivite::getChargeCardio).sorted().toList();
        double mediane = charges.size() % 2 == 1
                ? charges.get(charges.size() / 2)
                : (charges.get(charges.size() / 2 - 1) + charges.get(charges.size() / 2)) / 2.0;
        if (mediane <= 0) return "";

        return String.format(Locale.FRANCE, "Contre une médiane de charge cardio de %.0f sur les %d dernières"
                + " séances de %s.", mediane, historique.size(), a.getTypeActivite());
    }

    private String comparerAMediane(String libelle, Double valeurDuJour, List<SanteJour> historique,
            java.util.function.Function<SanteJour, Double> extraire, String unite) {
        if (valeurDuJour == null) return "";
        List<Double> valeurs = historique.stream().map(extraire).filter(java.util.Objects::nonNull).sorted()
                .toList();
        if (valeurs.size() < 5) return "";
        double mediane = valeurs.size() % 2 == 1
                ? valeurs.get(valeurs.size() / 2)
                : (valeurs.get(valeurs.size() / 2 - 1) + valeurs.get(valeurs.size() / 2)) / 2.0;
        if (mediane == 0) return "";
        double ecartPct = (valeurDuJour - mediane) / mediane * 100;
        String u = unite.isEmpty() ? "" : " " + unite;
        return String.format(Locale.FRANCE, "%s %.0f%s contre médiane %.0f%s (%+.0f%%). ", libelle, valeurDuJour, u,
                mediane, u, ecartPct);
    }

    private LocalDate parseDateOuAujourdhui(String date) {
        if (date == null || date.isBlank()) return LocalDate.now(clock);
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return LocalDate.now(clock);
        }
    }

    private String pas(Integer pas) {
        return pas == null ? "pas inconnus" : pas + " pas";
    }

    private String distance(Double distanceM) {
        return distanceM == null
                ? "distance inconnue"
                : String.format(Locale.FRANCE, "%.2f km", distanceM / 1000.0);
    }

    private String calories(Integer calories) {
        return calories == null ? "calories inconnues" : calories + " kcal";
    }

    private String minutesActives(Integer minutes) {
        return minutes == null ? "minutes actives inconnues" : minutes + " min actives";
    }

    private String fcRepos(Integer fcRepos) {
        return fcRepos == null ? "FC de repos inconnue" : "FC de repos " + fcRepos + " bpm";
    }

    private String scoreSommeil(Integer score) {
        return score == null ? "score de sommeil inconnu" : "score de sommeil " + score + "/100";
    }

    private String scorePreparation(Integer score) {
        return score == null ? "score de préparation inconnu" : "score de préparation " + score + "/100";
    }

    private String minMaxMoyenne(Integer min, Double moyenne, Integer max) {
        return "min " + (min == null ? "?" : min) + ", moyenne "
                + (moyenne == null ? "?" : Math.round(moyenne)) + ", max " + (max == null ? "?" : max) + " bpm";
    }

    private String minutesOuInconnu(Integer minutes) {
        return minutes == null ? "? min" : minutes + " min";
    }

    private String sousScore(Integer sousScore) {
        return sousScore == null ? "?" : String.valueOf(sousScore);
    }

    private String formatMinutesEnHeures(int minutes) {
        return (minutes / 60) + "h" + String.format("%02d", minutes % 60);
    }
}
