package com.kronos.chiron.push.service;

import com.kronos.chiron.utilisateur.model.Utilisateur;

public interface WebPushService {

    void envoyerAuxAbonnements(Utilisateur utilisateur, String titre, String corps, String url);
}
