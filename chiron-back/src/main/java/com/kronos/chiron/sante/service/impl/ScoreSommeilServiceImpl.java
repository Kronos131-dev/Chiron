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
    private static final double CIBLE_PCT_PROFOND_PARADOXAL = 0.40;
    private static final double CIBLE_RATIO_FC_SOMMEIL = 1.15;
    private static final double PLAGE_RATIO_FC_SOMMEIL = 0.30;
    private static final double PLAGE_PCT_AGITATION = 0.15;
    private static final int HISTORIQUE_VFC_JOURS = 30;
    private static final int HISTORIQUE_VFC_MIN_POINTS = 5;
    private static final double CREDIT_NEUTRE = 0.6;

    private final SanteSommeilRepository santeSommeilRepository;
    private final SanteJourRepository santeJourRepository;

    @Override
    public void recalculerPlage(Utilisateur utilisateur, LocalDate from, LocalDate to) {
        List<SanteSommeil> sessions = santeSommeilRepository
                .findByUtilisateurAndDateBetweenOrderByDebutAsc(utilisateur, from, to);
        for (SanteSommeil session : sessions) {
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

    private Integer scoreDuree(SanteSommeil session) {
        double ratio = Math.min(1.0, session.getMinutesEndormi() / (double) OBJECTIF_MINUTES);
        return (int) Math.round(ratio * 50);
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

    private double sousScoreAgitation(SanteSommeil session) {
        Integer eveille = session.getMinutesEveille();
        Integer agite = session.getMinutesAgite();
        if (eveille == null && agite == null) return 8 * CREDIT_NEUTRE;
        int total = nz(eveille) + nz(agite) + session.getMinutesEndormi();
        if (total <= 0) return 8 * CREDIT_NEUTRE;
        double pct = (nz(eveille) + nz(agite)) / (double) total;
        return clamp(8 * (1 - pct / PLAGE_PCT_AGITATION), 0, 8);
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
        return clamp(8 * (vfcJour / mediane), 0, 8);
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
