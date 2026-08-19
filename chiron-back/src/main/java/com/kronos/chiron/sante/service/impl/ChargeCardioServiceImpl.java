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
        if (jour.getMinutesZoneBruleuse() == null && jour.getMinutesZoneCardio() == null
                && jour.getMinutesZonePic() == null) {
            return null;
        }
        double bruleuse = nz(jour.getMinutesZoneBruleuse());
        double cardio = nz(jour.getMinutesZoneCardio());
        double pic = nz(jour.getMinutesZonePic());
        return bruleuse * ZoneCardiaque.BRULE_GRAISSE.poidsTrimp()
                + cardio * ZoneCardiaque.CARDIO.poidsTrimp()
                + pic * ZoneCardiaque.PIC.poidsTrimp();
    }

    private double nz(Integer v) {
        return v != null ? v : 0;
    }
}
