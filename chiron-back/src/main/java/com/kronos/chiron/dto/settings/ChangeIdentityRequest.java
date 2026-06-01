package com.kronos.chiron.dto.settings;

/** Mise à jour du prénom et du nom (utilisés notamment pour identifier les rapports Visbody). */
public record ChangeIdentityRequest(String prenom, String nom) {}
