package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.service.CaloriesSeanceService;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.utilisateur.model.Sexe;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
public class CaloriesSeanceServiceImpl implements CaloriesSeanceService {

    // WHY: mêmes défauts que CardioCalorieServiceImpl et CaloriesEffortServiceImpl — un profil
    // adulte moyen, pour qu'un profil incomplet donne un ordre de grandeur plutôt que rien.
    private static final double POIDS_DEFAUT_KG = 70.0;
    private static final int AGE_DEFAUT = 30;

    // WHY: valeur du Compendium of Physical Activities pour « resistance training, multiple
    // exercises, 8-15 répétitions, effort modéré ». Elle porte la séance entière, repos compris,
    // et non la série elle-même — c'est ce qui la rend comparable à une durée de séance.
    private static final double MET_MUSCULATION = 3.5;

    private static final double MINUTES_PAR_JOUR = 1440.0;
    private static final double ML_O2_PAR_KG_PAR_MET = 3.5;
    private static final double ML_O2_PAR_KCAL = 200.0;

    private final Clock clock;

    // WHY: le repli quand aucune fréquence cardiaque n'existe — compte Google Health non lié, ou
    // lié sans donnée sur le créneau. Le cardio garde ses calories déjà calculées série par série
    // à partir de la distance et de l'allure ; le reste de la séance est du temps sous barre, que
    // seul un modèle MET peut chiffrer. Le résultat est marqué comme estimé, jamais mesuré.
    @Override
    public Integer estimer(SanteActivite activite) {
        Seance seance = activite.getSeance();
        if (seance == null) return null;

        double minutesTotal = minutes(activite, seance);
        if (minutesTotal <= 0) return null;

        double kcalCardio = 0;
        double minutesCardio = 0;
        for (Exercice exercice : seance.getExercices()) {
            for (Serie serie : exercice.getSeries()) {
                if (serie.getCalories() != null) kcalCardio += serie.getCalories();
                if (serie.getDureeMin() != null) minutesCardio += serie.getDureeMin();
            }
        }

        double minutesMusculation = Math.max(0, minutesTotal - minutesCardio);
        double kcal = kcalCardio
                + MET_MUSCULATION * kcalParMetMinute(activite.getUtilisateur()) * minutesMusculation;
        return (int) Math.round(Math.max(0, kcal));
    }

    private double minutes(SanteActivite activite, Seance seance) {
        Duration duree = activite.getStartTime() != null && activite.getEndTime() != null
                ? Duration.between(activite.getStartTime(), activite.getEndTime())
                : bornes(seance);
        return duree == null ? 0 : duree.toSeconds() / 60.0;
    }

    private Duration bornes(Seance seance) {
        if (seance.getStartTime() == null || seance.getEndTime() == null) return null;
        return Duration.between(seance.getStartTime(), seance.getEndTime());
    }

    // WHY: un MET vaut le métabolisme de repos de celui qui s'entraîne, pas les 3,5 mL/kg/min de
    // la convention, qui décrivent un homme de 70 kg. Mifflin-St Jeor rend ce repos-là à partir
    // du poids, de la taille, de l'âge et du sexe — c'est là que la taille entre dans le calcul,
    // et ce qui distingue l'estimation d'un athlète de 1,60 m de celle d'un athlète de 1,95 m.
    private double kcalParMetMinute(Utilisateur utilisateur) {
        double poids = utilisateur.getPoidsCorps() != null && utilisateur.getPoidsCorps() > 0
                ? utilisateur.getPoidsCorps()
                : POIDS_DEFAUT_KG;
        Double taille = utilisateur.getTailleCm();
        if (taille == null || taille <= 0) {
            return ML_O2_PAR_KG_PAR_MET * poids / ML_O2_PAR_KCAL;
        }
        int age = utilisateur.getDateNaissance() != null
                ? Period.between(utilisateur.getDateNaissance(), LocalDate.now(clock)).getYears()
                : AGE_DEFAUT;
        double base = 10 * poids + 6.25 * taille - 5.0 * age;
        double metabolismeDeRepos = switch (utilisateur.getSexe() == null ? Sexe.AUTRE : utilisateur.getSexe()) {
            case HOMME -> base + 5;
            case FEMME -> base - 161;
            case AUTRE -> base - 78;
        };
        return Math.max(0, metabolismeDeRepos) / MINUTES_PAR_JOUR;
    }
}
