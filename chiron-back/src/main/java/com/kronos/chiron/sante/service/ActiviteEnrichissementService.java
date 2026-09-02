package com.kronos.chiron.sante.service;

import com.kronos.chiron.seance.model.Seance;

public interface ActiviteEnrichissementService {

    void planifierEnrichissement(Seance seance);

    void enregistrerPousseeGoogle(Long seanceId, String externalId);

    void tenterEnrichissement(Long activiteId);
}
