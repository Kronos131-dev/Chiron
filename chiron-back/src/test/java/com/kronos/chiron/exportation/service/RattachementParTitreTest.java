package com.kronos.chiron.exportation.service;

import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.seance.persistence.SeanceTitre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RattachementParTitreTest {

    @Mock
    private SeanceRepository seanceRepository;

    private RattachementParTitre rattachement;

    @BeforeEach
    void setUp() {
        rattachement = new RattachementParTitre(seanceRepository);
    }

    private static SeanceTitre seance(long id, String titre) {
        return new SeanceTitre() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getTitre() {
                return titre;
            }
        };
    }

    private static Map<Long, String> modeles(Object... paires) {
        Map<Long, String> modeles = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            modeles.put((Long) paires[i], (String) paires[i + 1]);
        }
        return modeles;
    }

    @Test
    void chercher_rattacheUneSeanceAuModeleDeMemeTitre() {
        when(seanceRepository.findTitresCompletesSansModele(1L)).thenReturn(List.of(seance(30L, "Push"),
                seance(31L, "Pull")));

        Map<Long, Long> rattachees = rattachement.chercher(1L, modeles(5L, "Push", 6L, "Pull"));

        assertThat(rattachees).containsEntry(30L, 5L).containsEntry(31L, 6L).hasSize(2);
    }

    @Test
    void chercher_ignoreLaCasseLesEspacesEtLesBlancsMultiples() {
        when(seanceRepository.findTitresCompletesSansModele(1L)).thenReturn(List.of(seance(30L, "  PUSH   day "),
                seance(31L, "séance du soir")));

        Map<Long, Long> rattachees = rattachement.chercher(1L, modeles(5L, "push day", 6L, "Séance du Soir"));

        assertThat(rattachees).containsEntry(30L, 5L).containsEntry(31L, 6L);
    }

    @Test
    void chercher_neRattachePasUneSeanceDontLeTitreNeCorrespondAAucunModele() {
        when(seanceRepository.findTitresCompletesSansModele(1L)).thenReturn(List.of(seance(30L, "Séance interactive"),
                seance(31L, null)));

        assertThat(rattachement.chercher(1L, modeles(5L, "Push"))).isEmpty();
    }

    @Test
    void chercher_ignoreUnTitreCarrePorteParDeuxModelesPourNePasDeviner() {
        when(seanceRepository.findTitresCompletesSansModele(1L)).thenReturn(List.of(seance(30L, "Push"),
                seance(31L, "Pull")));

        Map<Long, Long> rattachees = rattachement.chercher(1L, modeles(5L, "Push", 7L, " push ", 6L, "Pull"));

        assertThat(rattachees).doesNotContainKey(30L).containsEntry(31L, 6L);
    }

    @Test
    void chercher_ignoreLesModelesSansTitre() {
        when(seanceRepository.findTitresCompletesSansModele(1L)).thenReturn(List.of(seance(30L, ""),
                seance(31L, "   ")));

        assertThat(rattachement.chercher(1L, modeles(5L, "", 6L, "  ", 7L, null))).isEmpty();
    }

    @Test
    void chercher_sansModeleNInterrogePasLaBase() {
        assertThat(rattachement.chercher(1L, Map.of())).isEmpty();

        verify(seanceRepository, never()).findTitresCompletesSansModele(anyLong());
    }
}
