package com.kronos.chiron.exportation.service.impl;

import com.kronos.chiron.exportation.dto.ExportProgrammeDto;
import com.kronos.chiron.exportation.dto.ExportProgrammeRequest;
import com.kronos.chiron.exportation.dto.FichierExporte;
import com.kronos.chiron.exportation.dto.ModeleExport;
import com.kronos.chiron.exportation.dto.SeanceExport;
import com.kronos.chiron.exportation.model.ExportProgramme;
import com.kronos.chiron.exportation.model.ExportProgrammeExclusion;
import com.kronos.chiron.exportation.model.ExportProgrammeModele;
import com.kronos.chiron.exportation.model.ExportProgrammeSeance;
import com.kronos.chiron.exportation.persistence.ExportProgrammeRepository;
import com.kronos.chiron.exportation.service.ChargeurDonneesExport;
import com.kronos.chiron.exportation.service.RattachementParTitre;
import com.kronos.chiron.exportation.service.classeur.ClasseurXlsx;
import com.kronos.chiron.exportation.service.classeur.DonneesExport;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportProgrammeServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final LocalDateTime AVANT = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Mock
    private ExportProgrammeRepository exportRepository;
    @Mock
    private SeanceRepository seanceRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private ChargeurDonneesExport chargeurDonnees;
    @Mock
    private RattachementParTitre rattachementParTitre;
    @Mock
    private ClasseurXlsx classeurXlsx;

    private ExportProgrammeServiceImpl service;
    private Utilisateur alice;
    private Utilisateur bob;

    @BeforeEach
    void setUp() {
        service = new ExportProgrammeServiceImpl(exportRepository, seanceRepository, utilisateurRepository,
                chargeurDonnees, rattachementParTitre, classeurXlsx, CLOCK);
        alice = Utilisateur.builder().id(1L).username("alice").build();
        bob = Utilisateur.builder().id(2L).username("bob").build();
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(utilisateurRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(exportRepository.save(any())).thenAnswer(inv -> {
            ExportProgramme export = inv.getArgument(0);
            if (export.getId() == null) export.setId(9L);
            return export;
        });
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(seanceRepository.findIdsCompletesParModele(anyCollection())).thenReturn(List.of());
        when(rattachementParTitre.chercher(anyLong(), anyMap())).thenReturn(Map.of());
    }

    private Seance seance(long id, Utilisateur proprietaire, boolean historique) {
        Seance seance = new Seance();
        seance.setId(id);
        seance.setTitre("Séance " + id);
        seance.setHistorique(historique);
        seance.setUtilisateur(proprietaire);
        return seance;
    }

    private void donnerAuxModeles(Seance... seances) {
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(seances));
    }

    private ExportProgramme exportExistant(Utilisateur proprietaire, long... modeleIds) {
        ExportProgramme export = ExportProgramme.builder().id(9L).utilisateur(proprietaire).nom("Programme")
                .creeA(AVANT).modifieA(AVANT).build();
        int ordre = 0;
        for (long modeleId : modeleIds) {
            export.ajouterModele(ExportProgrammeModele.builder().modeleId(modeleId).ordre(ordre++).build());
        }
        when(exportRepository.findByIdAndUtilisateurId(9L, proprietaire.getId())).thenReturn(Optional.of(export));
        return export;
    }

    private static ExportProgrammeRequest requete(String nom, List<Long> modeleIds, List<SeanceExport> seances) {
        return new ExportProgrammeRequest(nom, modeleIds, seances, null);
    }

    private static ExportProgrammeRequest requete(String nom, List<Long> modeleIds, List<SeanceExport> seances,
            List<Long> exclues) {
        return new ExportProgrammeRequest(nom, modeleIds, seances, exclues);
    }

    private static HttpStatus statutDe(Throwable erreur) {
        return HttpStatus.valueOf(((ErrorResponseException) erreur).getStatusCode().value());
    }

    // --- création ---

    @Test
    void creer_enregistreLesModelesDansLOrdreSansDoublon() {
        donnerAuxModeles(seance(5L, alice, false), seance(6L, alice, false));

        ExportProgrammeDto dto = service.creer("alice", requete("  Programme force ", List.of(6L, 5L, 6L), List.of()));

        ArgumentCaptor<ExportProgramme> capture = ArgumentCaptor.forClass(ExportProgramme.class);
        verify(exportRepository).save(capture.capture());
        ExportProgramme export = capture.getValue();
        assertThat(export.getNom()).isEqualTo("Programme force");
        assertThat(export.getModeles()).extracting(ExportProgrammeModele::getModeleId, ExportProgrammeModele::getOrdre)
                .containsExactly(tuple(6L, 0), tuple(5L, 1));
        assertThat(export.getCreeA()).isEqualTo(MAINTENANT);
        assertThat(export.getModifieA()).isEqualTo(MAINTENANT);
        assertThat(export.getUtilisateur()).isSameAs(alice);
        assertThat(dto.id()).isEqualTo(9L);
        assertThat(dto.nom()).isEqualTo("Programme force");
        assertThat(dto.creeA()).isEqualTo(MAINTENANT);
    }

    @Test
    void creer_rattacheLesSeancesPasseesAuModeleChoisi() {
        donnerAuxModeles(seance(5L, alice, false), seance(6L, alice, false));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection()))
                .thenReturn(List.of(seance(30L, alice, true)));

        service.creer("alice", requete("E", List.of(5L, 6L), List.of(new SeanceExport(30L, 6L))));

        ArgumentCaptor<ExportProgramme> capture = ArgumentCaptor.forClass(ExportProgramme.class);
        verify(exportRepository).save(capture.capture());
        assertThat(capture.getValue().getSeances())
                .extracting(ExportProgrammeSeance::getSeanceId, ExportProgrammeSeance::getModeleId)
                .containsExactly(tuple(30L, 6L));
    }

    @Test
    void creer_refuseUnNomVideOuTropLong() {
        donnerAuxModeles(seance(5L, alice, false));

        assertThatThrownBy(() -> service.creer("alice", requete("   ", List.of(5L), List.of())))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.creer("alice", requete("x".repeat(121), List.of(5L), List.of())))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(exportRepository, never()).save(any());
    }

    @Test
    void creer_exigeAuMoinsUneSeanceDuProgramme() {
        assertThatThrownBy(() -> service.creer("alice", requete("E", List.of(), List.of())))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void creer_refuseUnModeleQuiAppartientAUnAutre() {
        donnerAuxModeles(seance(5L, bob, false));

        assertThatThrownBy(() -> service.creer("alice", requete("E", List.of(5L), List.of())))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(exportRepository, never()).save(any());
    }

    @Test
    void creer_refuseUneSeanceFaiteCommeModele() {
        donnerAuxModeles(seance(5L, alice, true));

        assertThatThrownBy(() -> service.creer("alice", requete("E", List.of(5L), List.of())))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void creer_refuseUnModeleInconnu() {
        assertThatThrownBy(() -> service.creer("alice", requete("E", List.of(5L), List.of())))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void creer_refuseTropDeModeles() {
        List<Long> ids = LongStream.rangeClosed(1, 21).boxed().toList();

        assertThatThrownBy(() -> service.creer("alice", requete("E", ids, List.of())))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void creer_refuseUneSeanceRattacheeAUnModeleHorsSelection() {
        donnerAuxModeles(seance(5L, alice, false));

        assertThatThrownBy(() -> service.creer("alice",
                requete("E", List.of(5L), List.of(new SeanceExport(30L, 99L)))))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void creer_refuseUneSeanceSansRattachement() {
        donnerAuxModeles(seance(5L, alice, false));

        assertThatThrownBy(() -> service.creer("alice",
                requete("E", List.of(5L), List.of(new SeanceExport(30L, null)))))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void creer_refuseUneSeanceAjouteeDeuxFois() {
        donnerAuxModeles(seance(5L, alice, false));

        assertThatThrownBy(() -> service.creer("alice", requete("E", List.of(5L),
                List.of(new SeanceExport(30L, 5L), new SeanceExport(30L, 5L)))))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void creer_refuseUneSeanceInexistanteOuInachevee() {
        donnerAuxModeles(seance(5L, alice, false));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.creer("alice",
                requete("E", List.of(5L), List.of(new SeanceExport(30L, 5L)))))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- séances exclues ---

    @Test
    void creer_memoriseLesSeancesExclues() {
        donnerAuxModeles(seance(5L, alice, false));
        when(seanceRepository.findAllById(List.of(40L, 41L))).thenReturn(
                List.of(seance(40L, alice, true), seance(41L, alice, true)));

        ExportProgrammeDto dto = service.creer("alice", requete("E", List.of(5L), List.of(), List.of(40L, 41L, 40L)));

        ArgumentCaptor<ExportProgramme> capture = ArgumentCaptor.forClass(ExportProgramme.class);
        verify(exportRepository).save(capture.capture());
        assertThat(capture.getValue().getExclusions()).extracting(ExportProgrammeExclusion::getSeanceId)
                .containsExactly(40L, 41L);
        assertThat(dto.seancesExclues()).containsExactly(40L, 41L);
    }

    @Test
    void creer_sansListeDExclusions_neMemoriseRien() {
        donnerAuxModeles(seance(5L, alice, false));

        ExportProgrammeDto dto = service.creer("alice", requete("E", List.of(5L), List.of()));

        assertThat(dto.seancesExclues()).isEmpty();
    }

    @Test
    void creer_refuseUneSeanceAjouteeEtExclue() {
        donnerAuxModeles(seance(5L, alice, false));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection()))
                .thenReturn(List.of(seance(30L, alice, true)));

        assertThatThrownBy(() -> service.creer("alice",
                requete("E", List.of(5L), List.of(new SeanceExport(30L, 5L)), List.of(30L))))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(exportRepository, never()).save(any());
    }

    @Test
    void creer_refuseUneExclusionQuiAppartientAUnAutreOuQuiEstUnModele() {
        donnerAuxModeles(seance(5L, alice, false));
        when(seanceRepository.findAllById(List.of(40L))).thenReturn(List.of(seance(40L, bob, true)));
        when(seanceRepository.findAllById(List.of(41L))).thenReturn(List.of(seance(41L, alice, false)));
        when(seanceRepository.findAllById(List.of(42L))).thenReturn(List.of());

        for (long id : new long[]{40L, 41L, 42L}) {
            assertThatThrownBy(() -> service.creer("alice", requete("E", List.of(5L), List.of(), List.of(id))))
                    .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(exportRepository, never()).save(any());
    }

    @Test
    void modifier_sansListeDExclusions_gardeLesExclusionsExistantes() {
        ExportProgramme export = exportExistant(alice, 5L);
        export.ajouterExclusion(ExportProgrammeExclusion.builder().seanceId(40L).build());
        donnerAuxModeles(seance(5L, alice, false));

        service.modifier("alice", 9L, requete("E", List.of(5L), List.of()));

        assertThat(export.getExclusions()).extracting(ExportProgrammeExclusion::getSeanceId).containsExactly(40L);
    }

    @Test
    void modifier_remplaceLesExclusionsParLaNouvelleListe() {
        ExportProgramme export = exportExistant(alice, 5L);
        export.ajouterExclusion(ExportProgrammeExclusion.builder().seanceId(40L).build());
        export.ajouterExclusion(ExportProgrammeExclusion.builder().seanceId(41L).build());
        donnerAuxModeles(seance(5L, alice, false));
        when(seanceRepository.findAllById(List.of(41L, 42L))).thenReturn(
                List.of(seance(41L, alice, true), seance(42L, alice, true)));

        service.modifier("alice", 9L, requete("E", List.of(5L), List.of(), List.of(41L, 42L)));

        assertThat(export.getExclusions()).extracting(ExportProgrammeExclusion::getSeanceId)
                .containsExactlyInAnyOrder(41L, 42L);
    }

    @Test
    void modifier_uneListeVideRendToutesLesSeancesALExport() {
        ExportProgramme export = exportExistant(alice, 5L);
        export.ajouterExclusion(ExportProgrammeExclusion.builder().seanceId(40L).build());
        donnerAuxModeles(seance(5L, alice, false));

        service.modifier("alice", 9L, requete("E", List.of(5L), List.of(), List.of()));

        assertThat(export.getExclusions()).isEmpty();
    }

    @Test
    void detail_neCompteQueLesSeancesNonExclues() {
        ExportProgramme export = exportExistant(alice, 5L);
        export.ajouterExclusion(ExportProgrammeExclusion.builder().seanceId(41L).build());
        donnerAuxModeles(seance(5L, alice, false));
        when(seanceRepository.findIdsCompletesParModele(anyCollection())).thenReturn(List.of(40L, 41L));
        when(rattachementParTitre.chercher(1L, Map.of(5L, "Séance 5"))).thenReturn(Map.of(42L, 5L, 41L, 5L));

        ExportProgrammeDto dto = service.detail("alice", 9L);

        assertThat(dto.nbSeances()).isEqualTo(2);
        assertThat(dto.seancesExclues()).containsExactly(41L);
    }

    // --- modification ---

    @Test
    void modifier_renommeEtReordonneEnGardantLesModelesConserves() {
        ExportProgramme export = exportExistant(alice, 5L, 6L);
        donnerAuxModeles(seance(6L, alice, false), seance(7L, alice, false));

        ExportProgrammeDto dto = service.modifier("alice", 9L, requete("Renommé", List.of(7L, 6L), List.of()));

        assertThat(export.getNom()).isEqualTo("Renommé");
        assertThat(export.getModeles()).extracting(ExportProgrammeModele::getModeleId, ExportProgrammeModele::getOrdre)
                .containsExactlyInAnyOrder(tuple(7L, 0), tuple(6L, 1));
        assertThat(export.getModifieA()).isEqualTo(MAINTENANT);
        assertThat(export.getCreeA()).isEqualTo(AVANT);
        assertThat(dto.nom()).isEqualTo("Renommé");
    }

    @Test
    void modifier_metsAJourLesSeancesAjouteesSansToucherCellesQuiRestent() {
        ExportProgramme export = exportExistant(alice, 5L, 6L);
        export.ajouterSeance(ExportProgrammeSeance.builder().seanceId(30L).modeleId(5L).build());
        export.ajouterSeance(ExportProgrammeSeance.builder().seanceId(31L).modeleId(5L).build());
        donnerAuxModeles(seance(5L, alice, false), seance(6L, alice, false));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection()))
                .thenReturn(List.of(seance(30L, alice, true), seance(32L, alice, true)));

        service.modifier("alice", 9L, requete("E", List.of(5L, 6L),
                List.of(new SeanceExport(30L, 6L), new SeanceExport(32L, 5L))));

        assertThat(export.getSeances())
                .extracting(ExportProgrammeSeance::getSeanceId, ExportProgrammeSeance::getModeleId)
                .containsExactlyInAnyOrder(tuple(30L, 6L), tuple(32L, 5L));
    }

    @Test
    void modifier_unExportDUnAutre_estIntrouvable() {
        assertThatThrownBy(() -> service.modifier("bob", 9L, requete("E", List.of(5L), List.of())))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(exportRepository, never()).save(any());
    }

    // --- lecture et suppression ---

    @Test
    void detail_rendLesTitresLesSeancesAjouteesEtLeNombreDeSeances() {
        ExportProgramme export = exportExistant(alice, 5L, 6L);
        export.ajouterSeance(ExportProgrammeSeance.builder().seanceId(30L).modeleId(5L).build());
        donnerAuxModeles(seance(5L, alice, false), seance(6L, alice, false));
        when(seanceRepository.findIdsCompletesParModele(anyCollection())).thenReturn(List.of(40L, 41L, 30L));

        ExportProgrammeDto dto = service.detail("alice", 9L);

        assertThat(dto.modeles()).containsExactly(new ModeleExport(5L, "Séance 5"), new ModeleExport(6L, "Séance 6"));
        assertThat(dto.seances()).containsExactly(new SeanceExport(30L, 5L));
        assertThat(dto.nbSeances()).isEqualTo(3);
        assertThat(dto.creeA()).isEqualTo(AVANT);
    }

    @Test
    void detail_compteAussiLesSeancesRattacheesParTitre() {
        ExportProgramme export = exportExistant(alice, 5L);
        donnerAuxModeles(seance(5L, alice, false));
        when(seanceRepository.findIdsCompletesParModele(anyCollection())).thenReturn(List.of(40L));
        when(rattachementParTitre.chercher(1L, Map.of(5L, "Séance 5"))).thenReturn(Map.of(41L, 5L, 42L, 5L));
        export.ajouterSeance(ExportProgrammeSeance.builder().seanceId(42L).modeleId(5L).build());

        ExportProgrammeDto dto = service.detail("alice", 9L);

        assertThat(dto.nbSeances()).isEqualTo(3);
    }

    @Test
    void detail_unExportDUnAutre_estIntrouvable() {
        exportExistant(alice, 5L);

        assertThatThrownBy(() -> service.detail("bob", 9L))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void lister_neRendQueLesExportsDeLUtilisateur() {
        ExportProgramme export = exportExistant(alice, 5L);
        when(exportRepository.findByUtilisateurIdOrderByCreeADesc(1L)).thenReturn(List.of(export));

        assertThat(service.lister("alice")).hasSize(1);
        verify(exportRepository, never()).findByUtilisateurIdOrderByCreeADesc(2L);
    }

    @Test
    void supprimer_effaceLExport() {
        ExportProgramme export = exportExistant(alice, 5L);

        service.supprimer("alice", 9L);

        verify(exportRepository).delete(export);
    }

    @Test
    void supprimer_unExportDUnAutre_estIntrouvable() {
        assertThatThrownBy(() -> service.supprimer("bob", 9L))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(exportRepository, never()).delete(any());
    }

    // --- téléchargement ---

    @Test
    void telecharger_genereLeFichierDepuisLeJournalAvecUnNomSansAccent() {
        ExportProgramme export = exportExistant(alice, 5L);
        export.setNom("Programme Force été");
        DonneesExport donnees = new DonneesExport("Programme Force été", MAINTENANT, List.of());
        when(chargeurDonnees.charger(export)).thenReturn(donnees);
        when(classeurXlsx.generer(donnees)).thenReturn(new byte[]{1, 2});

        FichierExporte fichier = service.telecharger("alice", 9L);

        assertThat(fichier.nom()).isEqualTo("Programme-Force-ete.xlsx");
        assertThat(fichier.contenu()).containsExactly(1, 2);
    }

    @Test
    void telecharger_nomSansCaractereUtilisable_retombeSurUnNomNeutre() {
        ExportProgramme export = exportExistant(alice, 5L);
        export.setNom("???");
        when(chargeurDonnees.charger(export)).thenReturn(new DonneesExport("???", MAINTENANT, List.of()));
        when(classeurXlsx.generer(any())).thenReturn(new byte[0]);

        assertThat(service.telecharger("alice", 9L).nom()).isEqualTo("export-chiron.xlsx");
    }

    @Test
    void telecharger_unExportDUnAutre_estIntrouvableEtNeGenereRien() {
        exportExistant(alice, 5L);

        assertThatThrownBy(() -> service.telecharger("bob", 9L))
                .satisfies(e -> assertThat(statutDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
        verify(classeurXlsx, never()).generer(any());
    }
}
