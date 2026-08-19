package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargeCardioServiceImplTest {

    @Mock
    private SanteJourRepository santeJourRepository;

    @InjectMocks
    private ChargeCardioServiceImpl chargeCardioService;

    private Utilisateur user;
    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(1L).username("athlete").build();
        from = LocalDate.of(2026, 8, 1);
        to = LocalDate.of(2026, 8, 1);
    }

    @Test
    void recalculerPlage_computesTrimpWeightedSumAcrossZones() {
        SanteJour jour = SanteJour.builder().utilisateur(user).date(from)
                .minutesZoneBruleuse(30).minutesZoneCardio(20).minutesZonePic(10).build();
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(user, from, to))
                .thenReturn(List.of(jour));

        chargeCardioService.recalculerPlage(user, from, to);

        assertThat(jour.getChargeCardio()).isEqualTo(100.0);
        verify(santeJourRepository).save(jour);
    }

    @Test
    void recalculerPlage_allZonesNull_leavesChargeCardioNull() {
        SanteJour jour = SanteJour.builder().utilisateur(user).date(from).build();
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(user, from, to))
                .thenReturn(List.of(jour));

        chargeCardioService.recalculerPlage(user, from, to);

        assertThat(jour.getChargeCardio()).isNull();
    }

    @Test
    void recalculerPlage_partialZoneData_treatsMissingZonesAsZero() {
        SanteJour jour = SanteJour.builder().utilisateur(user).date(from).minutesZoneCardio(15).build();
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(user, from, to))
                .thenReturn(List.of(jour));

        chargeCardioService.recalculerPlage(user, from, to);

        assertThat(jour.getChargeCardio()).isEqualTo(30.0);
    }
}
