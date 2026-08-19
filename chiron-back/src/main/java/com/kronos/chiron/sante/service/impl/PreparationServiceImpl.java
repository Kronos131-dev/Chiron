package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.sante.service.PreparationService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreparationServiceImpl implements PreparationService {

    // WHY: Google borne son score d'aptitude à trois zones — 1-29 faible, 30-64 modérée,
    // 65-100 élevée — et sur un mois complet une seule journée descend dans le rouge. Le
    // score ne part donc pas de zéro : trente points sont acquis, les soixante-dix autres
    // se gagnent. Sans ce plancher une mauvaise journée tombait à 26 là où Google donne 67.
    private static final int PLANCHER = 30;
    private static final int AMPLITUDE = 70;

    private static final int HISTORIQUE_JOURS = 30;
    private static final int HISTORIQUE_CHARGE_JOURS = 28;
    private static final int MIN_POINTS_BASELINE = 5;

    private static final double POIDS_VFC = 0.35;
    private static final double POIDS_CHARGE = 0.30;
    private static final double POIDS_SOMMEIL = 0.20;
    private static final double POIDS_FC_REPOS = 0.15;

    private static final double VFC_PLANCHER = 0.70;
    private static final double VFC_PLAGE = 0.55;
    private static final double CHARGE_TOLEREE = 0.30;
    private static final double CHARGE_PLAGE = 1.70;
    private static final double FC_PLAFOND = 1.06;
    private static final double FC_PLAGE = 0.12;

    private final SanteJourRepository santeJourRepository;
    private final SanteSommeilRepository santeSommeilRepository;

    @Override
    public Integer calculer(Utilisateur utilisateur, LocalDate date) {
        SanteJour jour = santeJourRepository.findByUtilisateurAndDate(utilisateur, date).orElse(null);
        if (jour == null || jour.getVfcMs() == null) return null;

        List<SanteJour> historique = santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(
                utilisateur, date.minusDays(HISTORIQUE_JOURS), date);

        Double medianeVfc = mediane(valeurs(historique, SanteJour::getVfcMs));
        if (medianeVfc == null || medianeVfc <= 0) return null;

        double total = POIDS_VFC * facteurVfc(jour.getVfcMs(), medianeVfc)
                + POIDS_CHARGE * facteurCharge(utilisateur, date)
                + POIDS_SOMMEIL * facteurSommeil(utilisateur, date)
                + POIDS_FC_REPOS * facteurFcRepos(jour, historique);

        return (int) Math.round(PLANCHER + AMPLITUDE * borner(total, 0, 1));
    }

    // WHY: la charge de la veille explique les quatre valeurs relevées chez Google mieux
    // que celle du jour même — le 18/08 porte une charge de 129 et une aptitude de 99,
    // parce que le 17 était un jour de repos. C'est un indice de récupération, pas de forme.
    private double facteurCharge(Utilisateur utilisateur, LocalDate date) {
        LocalDate veille = date.minusDays(1);
        Double chargeVeille = santeJourRepository.findByUtilisateurAndDate(utilisateur, veille)
                .map(SanteJour::getChargeCardio)
                .orElse(null);
        if (chargeVeille == null) return neutre();

        List<Double> charges = valeurs(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(
                utilisateur, date.minusDays(HISTORIQUE_CHARGE_JOURS), veille), SanteJour::getChargeCardio);
        if (charges.size() < MIN_POINTS_BASELINE) return neutre();

        double moyenne = charges.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (moyenne <= 0) return neutre();

        double rapport = chargeVeille / moyenne;
        return borner(1 - (rapport - CHARGE_TOLEREE) / CHARGE_PLAGE, 0, 1);
    }

    private double facteurSommeil(Utilisateur utilisateur, LocalDate date) {
        Integer score = santeSommeilRepository
                .findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(utilisateur, date)
                .map(SanteSommeil::getScore)
                .orElse(null);
        return score == null ? neutre() : borner(score / 100.0, 0, 1);
    }

    private double facteurFcRepos(SanteJour jour, List<SanteJour> historique) {
        if (jour.getFcRepos() == null) return neutre();
        List<Double> repos = valeurs(historique, j -> j.getFcRepos() == null ? null : (double) j.getFcRepos());
        Double mediane = mediane(repos);
        if (mediane == null || mediane <= 0) return neutre();
        return borner((FC_PLAFOND - jour.getFcRepos() / mediane) / FC_PLAGE, 0, 1);
    }

    private double facteurVfc(double vfc, double mediane) {
        return borner((vfc / mediane - VFC_PLANCHER) / VFC_PLAGE, 0, 1);
    }

    // WHY: une composante absente ne doit ni sanctionner ni récompenser. Elle vaut donc la
    // moitié de son poids, faute de quoi un signal manquant ferait plonger le score.
    private double neutre() {
        return 0.5;
    }

    private List<Double> valeurs(List<SanteJour> jours, java.util.function.Function<SanteJour, Double> extraire) {
        List<Double> resultat = new ArrayList<>(jours.stream().map(extraire).filter(Objects::nonNull).toList());
        resultat.sort(Double::compareTo);
        return resultat;
    }

    private Double mediane(List<Double> triees) {
        if (triees.size() < MIN_POINTS_BASELINE) return null;
        int n = triees.size();
        return n % 2 == 1 ? triees.get(n / 2) : (triees.get(n / 2 - 1) + triees.get(n / 2)) / 2.0;
    }

    private double borner(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
