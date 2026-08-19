package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.sante.service.ScoreSommeilService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ScoreSommeilServiceImpl implements ScoreSommeilService {

    private static final int OBJECTIF_MINUTES = 480;
    private static final double CIBLE_PCT_PROFOND_PARADOXAL = 0.50;
    private static final double CIBLE_RATIO_FC_SOMMEIL = 1.10;
    private static final double PLAGE_RATIO_FC_SOMMEIL = 0.25;
    private static final double PLAGE_PCT_AGITATION = 0.15;
    private static final double POIDS_PART_EVEIL = 5.0;
    private static final double POIDS_FRAGMENTATION = 3.0;
    private static final double REVEILS_PAR_HEURE_MAX = 3.0;
    private static final int HISTORIQUE_VFC_JOURS = 30;
    private static final int HISTORIQUE_VFC_MIN_POINTS = 5;
    private static final double CREDIT_NEUTRE = 0.45;
    private static final double VFC_RAPPORT_PLANCHER = 0.75;
    private static final double VFC_RAPPORT_PLAGE = 0.50;

    private final SanteSommeilRepository santeSommeilRepository;
    private final SanteJourRepository santeJourRepository;

    @Override
    public void recalculerPlage(Utilisateur utilisateur, LocalDate from, LocalDate to) {
        List<SanteSommeil> sessions = santeSommeilRepository
                .findByUtilisateurAndDateBetweenOrderByDebutAsc(utilisateur, from, to);
        for (SanteSommeil session : sessions) {
            if (session.isSieste()) continue;
            if (session.getMinutesEndormi() == null || session.getMinutesEndormi() <= 0) continue;
            Integer scoreDuree = scoreDuree(session);
            Integer scoreComposition = scoreComposition(session);
            int scoreRestauration = scoreRestauration(utilisateur, session);
            session.setScoreDuree(scoreDuree);
            session.setScoreComposition(scoreComposition);
            session.setScoreRestauration(scoreRestauration);
            session.setScore(nz(scoreDuree) + nz(scoreComposition) + scoreRestauration);
            santeSommeilRepository.save(session);
        }
    }

    // WHY: barème linéaire, 6h43 valait encore 42 points sur 50 — 84 % du maximum pour une
    // nuit d'une heure et quart trop courte. Le carré rapproche la pénalité de ce que
    // Google applique : à durée pleine il donne 82-83 au total, ce qui ne laisse que
    // 32 points aux deux autres composantes.
    private Integer scoreDuree(SanteSommeil session) {
        double ratio = Math.min(1.0, session.getMinutesEndormi() / (double) OBJECTIF_MINUTES);
        return (int) Math.round(ratio * ratio * 50);
    }

    private Integer scoreComposition(SanteSommeil session) {
        if (!session.isStadesDisponibles()) return null;
        int profond = nz(session.getMinutesProfond());
        int paradoxal = nz(session.getMinutesParadoxal());
        double pct = (profond + paradoxal) / (double) session.getMinutesEndormi();
        double ratio = Math.min(1.0, pct / CIBLE_PCT_PROFOND_PARADOXAL);
        return (int) Math.round(ratio * 25);
    }

    private int scoreRestauration(Utilisateur utilisateur, SanteSommeil session) {
        double hr = sousScoreFrequenceCardiaque(utilisateur, session);
        double agitation = sousScoreAgitation(session);
        double vfc = sousScoreVfc(utilisateur, session);
        return (int) Math.round(hr + agitation + vfc);
    }

    private double sousScoreFrequenceCardiaque(Utilisateur utilisateur, SanteSommeil session) {
        Integer fcRepos = santeJourRepository.findByUtilisateurAndDate(utilisateur, session.getDate())
                .map(SanteJour::getFcRepos)
                .orElse(null);
        if (session.getFcSommeilMoyenne() == null || fcRepos == null || fcRepos <= 0) {
            return 9 * CREDIT_NEUTRE;
        }
        double ratio = session.getFcSommeilMoyenne() / fcRepos;
        return clamp(9 * (CIBLE_RATIO_FC_SOMMEIL - ratio) / PLAGE_RATIO_FC_SOMMEIL, 0, 9);
    }

    // WHY: Google Health ne publie pas de stade « agité ». Son modèle ne connaît que
    // AWAKE, LIGHT, DEEP et REM — minutesAgite vient d'un stade RESTLESS propre au Fitbit
    // classique, et reste donc toujours nul. Le compter comme zéro minute d'agitation
    // revenait à créditer une nuit parfaitement calme et donnait les 8 points d'office.
    // L'agitation se déduit ici des deux signaux que Google fournit vraiment : la part de
    // temps passée éveillé, et le nombre de réveils lu dans le count du stade AWAKE.
    private double sousScoreAgitation(SanteSommeil session) {
        Double partEveil = sousScorePartEveil(session);
        Double fragmentation = sousScoreFragmentation(session);
        if (partEveil == null && fragmentation == null) return 8 * CREDIT_NEUTRE;
        double eveil = partEveil != null ? partEveil : POIDS_PART_EVEIL * CREDIT_NEUTRE;
        double reveils = fragmentation != null ? fragmentation : POIDS_FRAGMENTATION * CREDIT_NEUTRE;
        return eveil + reveils;
    }

    private Double sousScorePartEveil(SanteSommeil session) {
        Integer eveille = session.getMinutesEveille();
        Integer agite = session.getMinutesAgite();
        if (eveille == null && agite == null) return null;
        int perturbe = nz(eveille) + nz(agite);
        int total = perturbe + session.getMinutesEndormi();
        if (total <= 0) return null;
        double pct = perturbe / (double) total;
        return clamp(POIDS_PART_EVEIL * (1 - pct / PLAGE_PCT_AGITATION), 0, POIDS_PART_EVEIL);
    }

    private Double sousScoreFragmentation(SanteSommeil session) {
        Integer reveils = session.getNbReveils();
        if (reveils == null) return null;
        double heures = session.getMinutesEndormi() / 60.0;
        if (heures <= 0) return null;
        double parHeure = reveils / heures;
        return clamp(POIDS_FRAGMENTATION * (1 - parHeure / REVEILS_PAR_HEURE_MAX), 0, POIDS_FRAGMENTATION);
    }

    private double sousScoreVfc(Utilisateur utilisateur, SanteSommeil session) {
        Double vfcJour = santeJourRepository.findByUtilisateurAndDate(utilisateur, session.getDate())
                .map(SanteJour::getVfcMs)
                .orElse(null);
        if (vfcJour == null) return 8 * CREDIT_NEUTRE;

        LocalDate depuis = session.getDate().minusDays(HISTORIQUE_VFC_JOURS);
        List<Double> historique = santeJourRepository
                .findByUtilisateurAndDateBetweenOrderByDateAsc(utilisateur, depuis, session.getDate())
                .stream()
                .map(SanteJour::getVfcMs)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (historique.size() < HISTORIQUE_VFC_MIN_POINTS) return 8 * CREDIT_NEUTRE;

        double mediane = mediane(historique);
        if (mediane <= 0) return 8 * CREDIT_NEUTRE;
        // WHY: le rapport brut donnait les 8 points dès que la VFC atteignait sa propre
        // médiane — donc une nuit parfaitement banale décrochait la note maximale. La
        // médiane vaut désormais la moitié des points, et il faut la dépasser d'un quart
        // pour saturer.
        double rapport = vfcJour / mediane;
        return clamp(8 * (rapport - VFC_RAPPORT_PLANCHER) / VFC_RAPPORT_PLAGE, 0, 8);
    }

    private double mediane(List<Double> valeursTriees) {
        int n = valeursTriees.size();
        return n % 2 == 1
                ? valeursTriees.get(n / 2)
                : (valeursTriees.get(n / 2 - 1) + valeursTriees.get(n / 2)) / 2.0;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
    }
}
