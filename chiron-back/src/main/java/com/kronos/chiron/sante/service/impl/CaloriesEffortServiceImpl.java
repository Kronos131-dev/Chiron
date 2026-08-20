package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.sante.service.CaloriesEffortService;
import com.kronos.chiron.utilisateur.model.Sexe;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CaloriesEffortServiceImpl implements CaloriesEffortService {

    // WHY: à défaut de poids ou d'âge renseignés, ces valeurs reproduisent un profil
    // adulte moyen — même défaut de poids que CardioCalorieServiceImpl.
    private static final double POIDS_DEFAUT_KG = 70.0;
    private static final int AGE_DEFAUT = 30;
    private static final int MINUTES_PAR_BUCKET = 5;

    private final Clock clock;

    @Override
    public int estimer(Utilisateur utilisateur, List<SanteFrequenceCardiaque> buckets) {
        double poids = utilisateur.getPoidsCorps() != null && utilisateur.getPoidsCorps() > 0
                ? utilisateur.getPoidsCorps()
                : POIDS_DEFAUT_KG;
        int age = utilisateur.getDateNaissance() != null
                ? Period.between(utilisateur.getDateNaissance(), LocalDate.now(clock)).getYears()
                : AGE_DEFAUT;

        double total = 0;
        for (SanteFrequenceCardiaque bucket : buckets) {
            if (bucket.getFcMoyenne() == null) continue;
            total += kcalParMinute(bucket.getFcMoyenne(), poids, age, utilisateur.getSexe()) * MINUTES_PAR_BUCKET;
        }
        return (int) Math.round(Math.max(0, total));
    }

    // WHY: formule de Keytel (2005), la seule estimation de dépense énergétique à partir
    // de la seule fréquence cardiaque — CardioCalorieService est un modèle MET qui exige
    // vitesse/pente/distance, indisponibles pour une séance de musculation.
    private double kcalParMinute(double fc, double poidsKg, int age, Sexe sexe) {
        double homme = (-55.0969 + 0.6309 * fc + 0.1988 * poidsKg + 0.2017 * age) / 4.184;
        double femme = (-20.4022 + 0.4472 * fc - 0.1263 * poidsKg + 0.0740 * age) / 4.184;
        double kcal = switch (sexe == null ? Sexe.AUTRE : sexe) {
            case HOMME -> homme;
            case FEMME -> femme;
            case AUTRE -> (homme + femme) / 2.0;
        };
        return Math.max(0, kcal);
    }
}
