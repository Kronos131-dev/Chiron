package com.kronos.chiron.sante.service;

import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.utilisateur.model.Utilisateur;

public interface ActiviteFusionService {

    void fusionnerFenetre(Utilisateur utilisateur, int joursFenetre);

    void fusionnerActivite(SanteActivite chiron);
}
