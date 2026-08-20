package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.dto.SeuilsCardiaquesDto;
import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.service.SeuilsCardiaquesService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeuilsCardiaquesServiceImpl implements SeuilsCardiaquesService {

    // WHY: sans date de naissance ni FC de repos mesurée, ces valeurs reproduisent un
    // profil adulte moyen plutôt que de bloquer le calcul de zone.
    private static final int AGE_DEFAUT = 30;
    private static final int FC_REPOS_DEFAUT = 60;
    private static final int FC_MAX_BASE = 220;
    private static final int HISTORIQUE_FC_REPOS_JOURS = 30;

    private static final double RATIO_MODERE = 0.50;
    private static final double RATIO_INTENSE = 0.70;
    private static final double RATIO_MAXIMUM = 0.85;

    private final SanteJourRepository santeJourRepository;
    private final Clock clock;

    @Override
    public SeuilsCardiaquesDto calculer(Utilisateur utilisateur) {
        LocalDate today = LocalDate.now(clock);
        int age = utilisateur.getDateNaissance() != null
                ? Period.between(utilisateur.getDateNaissance(), today).getYears()
                : AGE_DEFAUT;
        int fcMax = FC_MAX_BASE - age;

        int fcRepos = santeJourRepository
                .findFirstByUtilisateurAndFcReposIsNotNullAndDateBetweenOrderByDateDesc(utilisateur,
                        today.minusDays(HISTORIQUE_FC_REPOS_JOURS), today)
                .map(SanteJour::getFcRepos)
                .orElse(FC_REPOS_DEFAUT);

        int reserve = fcMax - fcRepos;
        int modere = fcRepos + (int) Math.round(RATIO_MODERE * reserve);
        int intense = fcRepos + (int) Math.round(RATIO_INTENSE * reserve);
        int maximum = fcRepos + (int) Math.round(RATIO_MAXIMUM * reserve);
        return new SeuilsCardiaquesDto(modere, intense, maximum);
    }
}
