package com.kronos.chiron.sante.service;

import com.kronos.chiron.sante.dto.SeuilsCardiaquesDto;
import com.kronos.chiron.utilisateur.model.Utilisateur;

public interface SeuilsCardiaquesService {

    SeuilsCardiaquesDto calculer(Utilisateur utilisateur);
}
