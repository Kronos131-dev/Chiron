package com.kronos.chiron.utilisateur.dto;

/** Mise à jour du prénom et du nom (utilisés notamment pour identifier les rapports Visbody). */
public record ChangeIdentityRequest(String prenom, String nom) {}
