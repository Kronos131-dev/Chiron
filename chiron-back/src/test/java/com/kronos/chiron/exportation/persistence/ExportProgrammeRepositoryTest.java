package com.kronos.chiron.exportation.persistence;

import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.exportation.model.ExportProgramme;
import com.kronos.chiron.exportation.model.ExportProgrammeModele;
import com.kronos.chiron.exportation.model.ExportProgrammeSeance;
import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ExportProgrammeRepositoryTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ExportProgrammeRepository repository;
    @Autowired
    private SeanceRepository seanceRepository;

    private Utilisateur alice;
    private Utilisateur bob;
    private Seance modele;
    private Seance autreModele;

    @BeforeEach
    void setUp() {
        alice = utilisateur("alice");
        bob = utilisateur("bob");
        modele = seance(alice, "Push A", false, null, null, null);
        autreModele = seance(alice, "Pull B", false, null, null, null);
    }

    private Utilisateur utilisateur(String username) {
        Utilisateur user = Utilisateur.builder().username(username).password("x").role(Role.USER).isPublic(false)
                .build();
        em.persist(user);
        return user;
    }

    private Seance seance(Utilisateur proprietaire, String titre, boolean historique, Long modeleId,
            LocalDateTime fin, String exercice) {
        Seance seance = new Seance();
        seance.setTitre(titre);
        seance.setHistorique(historique);
        seance.setModeleId(modeleId);
        seance.setStartTime(T0.minusHours(2));
        seance.setEndTime(fin);
        seance.setUtilisateur(proprietaire);
        if (exercice != null) {
            Exercice exo = new Exercice();
            exo.setNom(exercice);
            seance.addExercice(exo);
        }
        em.persist(seance);
        em.flush();
        return seance;
    }

    private ExportProgramme export(Utilisateur proprietaire, Seance... modeles) {
        ExportProgramme export = ExportProgramme.builder().utilisateur(proprietaire).nom("Programme").creeA(T0)
                .modifieA(T0).build();
        int ordre = 0;
        for (Seance m : modeles) {
            export.ajouterModele(ExportProgrammeModele.builder().modeleId(m.getId()).ordre(ordre++).build());
        }
        em.persist(export);
        em.flush();
        em.clear();
        return export;
    }

    private ExportProgramme recharge(ExportProgramme export) {
        em.clear();
        return repository.findById(export.getId()).orElseThrow();
    }

    @Test
    void findByUtilisateurIdOrderByCreeADesc_rendLesPlusRecentsEnPremierEtSeulementLesSiens() {
        ExportProgramme ancien = export(alice, modele);
        ExportProgramme recent = export(alice, autreModele);
        export(bob, modele);
        em.clear();
        ExportProgramme plusRecent = repository.findById(recent.getId()).orElseThrow();
        plusRecent.setCreeA(T0.plusDays(1));
        em.flush();
        em.clear();

        assertThat(repository.findByUtilisateurIdOrderByCreeADesc(alice.getId())).extracting(ExportProgramme::getId)
                .containsExactly(recent.getId(), ancien.getId());
    }

    @Test
    void findById_rendLesModelesDansLOrdreChoisi() {
        ExportProgramme export = export(alice, autreModele, modele);

        assertThat(recharge(export).getModeles()).extracting(ExportProgrammeModele::getModeleId)
                .containsExactly(autreModele.getId(), modele.getId());
    }

    @Test
    void findByIdAndUtilisateurId_rendLesSeancesAjouteesALaMain() {
        Seance faite = seance(alice, "Push A", true, null, T0, "Squat");
        ExportProgramme export = export(alice, modele);
        ExportProgramme pris = repository.findById(export.getId()).orElseThrow();
        pris.ajouterSeance(ExportProgrammeSeance.builder().seanceId(faite.getId()).modeleId(modele.getId()).build());
        em.flush();
        em.clear();

        ExportProgramme relu = repository.findByIdAndUtilisateurId(export.getId(), alice.getId()).orElseThrow();

        assertThat(relu.getSeances()).extracting(ExportProgrammeSeance::getSeanceId, ExportProgrammeSeance::getModeleId)
                .containsExactly(org.assertj.core.api.Assertions.tuple(faite.getId(), modele.getId()));
    }

    @Test
    void delete_emporteLesLignesDeLExport() {
        ExportProgramme export = export(alice, modele);

        repository.delete(repository.findById(export.getId()).orElseThrow());
        em.flush();

        assertThat(repository.findById(export.getId())).isEmpty();
        Number lignes = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM export_programme_modele WHERE export_id = :id")
                .setParameter("id", export.getId()).getSingleResult();
        assertThat(lignes.longValue()).isZero();
    }

    @Test
    void findByIdAndUtilisateurId_nExposePasLesExportsDesAutres() {
        ExportProgramme export = export(alice, modele);

        assertThat(repository.findByIdAndUtilisateurId(export.getId(), alice.getId())).isPresent();
        assertThat(repository.findByIdAndUtilisateurId(export.getId(), bob.getId())).isEmpty();
    }

    // --- séances faites ---

    @Test
    void findCompletesParModele_ecarteLesSeancesNonTermineesEtCellesDUnAutreModele() {
        Seance complete = seance(alice, "Push A", true, modele.getId(), T0, "Squat");
        Seance sansExercice = seance(alice, "Push A", true, modele.getId(), T0, null);
        seance(alice, "Push A", true, modele.getId(), null, "Squat");
        seance(alice, "Pull B", true, autreModele.getId(), T0, "Rowing");

        assertThat(seanceRepository.findCompletesParModele(List.of(modele.getId()))).extracting(Seance::getId)
                .containsExactlyInAnyOrder(complete.getId(), sansExercice.getId());
    }

    @Test
    void findIdsCompletesParModele_ecarteAussiLesSeancesSansExercice() {
        Seance complete = seance(alice, "Push A", true, modele.getId(), T0, "Squat");
        seance(alice, "Push A", true, modele.getId(), T0, null);
        seance(alice, "Push A", true, modele.getId(), null, "Squat");

        assertThat(seanceRepository.findIdsCompletesParModele(List.of(modele.getId())))
                .containsExactly(complete.getId());
    }

    @Test
    void findCompletesDeLUtilisateur_neRendQueSesSeancesFaitesEtTerminees() {
        Seance sienne = seance(alice, "Push", true, null, T0, "Squat");
        Seance deBob = seance(bob, "Push", true, null, T0, "Squat");
        Seance modeleSeul = seance(alice, "Modèle", false, null, null, "Squat");

        List<Seance> trouvees = seanceRepository.findCompletesDeLUtilisateur(alice.getId(),
                List.of(sienne.getId(), deBob.getId(), modeleSeul.getId()));

        assertThat(trouvees).extracting(Seance::getId).containsExactly(sienne.getId());
    }
}
