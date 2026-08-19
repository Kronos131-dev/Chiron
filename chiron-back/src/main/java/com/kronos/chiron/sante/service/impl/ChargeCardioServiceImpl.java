package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.model.ZoneCardiaque;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.service.ChargeCardioService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChargeCardioServiceImpl implements ChargeCardioService {

    private final SanteJourRepository santeJourRepository;

    @Override
    public void recalculerPlage(Utilisateur utilisateur, LocalDate from, LocalDate to) {
        List<SanteJour> jours = santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(utilisateur, from,
                to);
        for (SanteJour jour : jours) {
            Double charge = calculer(jour);
            if (charge != null) {
                jour.setChargeCardio(charge);
                santeJourRepository.save(jour);
            }
        }
    }

    private Double calculer(SanteJour jour) {
        return ZoneCardiaque.chargeCardio(jour.getMinutesZoneBruleuse(), jour.getMinutesZoneCardio(),
                jour.getMinutesZonePic());
    }
}
