package com.kronos.chiron.sante.service;

import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import java.util.List;

public interface CaloriesEffortService {

    int estimer(Utilisateur utilisateur, List<SanteFrequenceCardiaque> buckets);
}
