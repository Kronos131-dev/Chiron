package com.kronos.chiron.service;

import com.kronos.chiron.dto.ExerciceDto;
import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.dto.SerieDto;
import com.kronos.chiron.entity.*;
import com.kronos.chiron.repository.SeanceRepository;
import com.kronos.chiron.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgrammeServiceTest {

    @Mock private SeanceRepository seanceRepository;
    @Mock private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private ProgrammeService programmeService;

    private Utilisateur owner;
    private Utilisateur otherUser;

    @BeforeEach
    void setUp() {
        owner = Utilisateur.builder().id(1L).username("owner").role(Role.USER).isPublic(true).build();
        otherUser = Utilisateur.builder().id(2L).username("other").role(Role.USER).isPublic(false).build();

        when(seanceRepository.save(any())).thenAnswer(inv -> {
            Seance s = inv.getArgument(0);
            if (s.getId() == null) s.setId(99L);
            return s;
        });
    }

    // --- sauvegarderProgramme (new) ---

    @Test
    void sauvegarderProgramme_newProgramme_savesWithOwner() {
        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

        SeanceDto dto = new SeanceDto(null, "Leg Day", LocalDateTime.now(), null, 1, false, null,
                List.of(new ExerciceDto(null, "Squat", null, null,
                        List.of(new SerieDto(100.0, 5, null, null)))));

        Seance result = programmeService.sauvegarderProgramme("owner", dto);

        verify(seanceRepository).save(argThat(s ->
                s.getTitre().equals("Leg Day") &&
                s.getUtilisateur().equals(owner) &&
                !s.isModele()
        ));
    }

    @Test
    void sauvegarderProgramme_noExercices_savesEmptyProgramme() {
        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

        SeanceDto dto = new SeanceDto(null, "Empty", null, null, null, false, null, List.of());

        programmeService.sauvegarderProgramme("owner", dto);

        verify(seanceRepository).save(argThat(s -> s.getExercices().isEmpty()));
    }

    @Test
    void sauvegarderProgramme_updateExistingByOwner_updatesFields() {
        Seance existing = new Seance();
        existing.setId(5L);
        existing.setTitre("Old");
        existing.setModele(false);
        existing.setUtilisateur(owner);

        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(seanceRepository.findById(5L)).thenReturn(Optional.of(existing));

        SeanceDto dto = new SeanceDto(5L, "New Title", null, null, null, false, null, List.of());

        programmeService.sauvegarderProgramme("owner", dto);

        assertThat(existing.getTitre()).isEqualTo("New Title");
        verify(seanceRepository).save(existing);
    }

    @Test
    void sauvegarderProgramme_updateExisting_preservesExerciceOrderFromDto() {
        Seance existing = new Seance();
        existing.setId(7L);
        existing.setTitre("Push");
        existing.setModele(false);
        existing.setUtilisateur(owner);
        // Pre-populate with exercises in some order to confirm the service clears them.
        Exercice old = new Exercice();
        old.setNom("Old");
        existing.addExercice(old);

        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(seanceRepository.findById(7L)).thenReturn(Optional.of(existing));

        SeanceDto dto = new SeanceDto(7L, "Push", null, null, null, false, null,
                List.of(
                        new ExerciceDto(null, "Dips",  null, null, List.of()),
                        new ExerciceDto(null, "Bench", null, null, List.of()),
                        new ExerciceDto(null, "Squat", null, null, List.of())
                ));

        programmeService.sauvegarderProgramme("owner", dto);

        // Hibernate persists @OrderColumn from list position, so the order saved on the
        // entity's `exercices` list IS the order users see when the seance is reloaded.
        assertThat(existing.getExercices())
                .extracting(Exercice::getNom)
                .containsExactly("Dips", "Bench", "Squat");
    }

    @Test
    void sauvegarderProgramme_updateByUnauthorized_throwsException() {
        Seance existing = new Seance();
        existing.setId(5L);
        existing.setUtilisateur(owner);
        existing.setModele(false);

        when(utilisateurRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(seanceRepository.findById(5L)).thenReturn(Optional.of(existing));

        SeanceDto dto = new SeanceDto(5L, "Hack", null, null, null, false, null, List.of());

        assertThatThrownBy(() -> programmeService.sauvegarderProgramme("other", dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    void sauvegarderProgramme_updateByCoach_succeeds() {
        Utilisateur coach = Utilisateur.builder().id(3L).username("coach").role(Role.USER).build();
        owner.addCoach(coach);

        Seance existing = new Seance();
        existing.setId(5L);
        existing.setUtilisateur(owner);
        existing.setModele(false);

        when(utilisateurRepository.findByUsername("coach")).thenReturn(Optional.of(coach));
        when(seanceRepository.findById(5L)).thenReturn(Optional.of(existing));

        SeanceDto dto = new SeanceDto(5L, "Coach Edit", null, null, null, false, null, List.of());

        programmeService.sauvegarderProgramme("coach", dto);

        verify(seanceRepository).save(any());
    }

    // --- getProgrammes ---

    @Test
    void getProgrammes_returnsListFromRepository() {
        Seance s = new Seance();
        s.setTitre("My Programme");
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc("owner"))
                .thenReturn(List.of(s));

        List<Seance> result = programmeService.getProgrammes("owner");

        assertThat(result).hasSize(1);
    }

    // --- reorderProgrammes ---

    @Test
    void reorderProgrammes_byOwner_assignsContiguousPositions() {
        Seance s1 = new Seance(); s1.setId(10L); s1.setUtilisateur(owner); s1.setDisplayOrder(5);
        Seance s2 = new Seance(); s2.setId(20L); s2.setUtilisateur(owner); s2.setDisplayOrder(2);
        Seance s3 = new Seance(); s3.setId(30L); s3.setUtilisateur(owner); s3.setDisplayOrder(7);

        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(seanceRepository.findAllById(List.of(30L, 10L, 20L))).thenReturn(List.of(s1, s2, s3));

        programmeService.reorderProgrammes("owner", List.of(30L, 10L, 20L));

        assertThat(s3.getDisplayOrder()).isEqualTo(0);
        assertThat(s1.getDisplayOrder()).isEqualTo(1);
        assertThat(s2.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void reorderProgrammes_nullOrEmptyList_isNoOp() {
        programmeService.reorderProgrammes("owner", null);
        programmeService.reorderProgrammes("owner", List.of());

        verifyNoInteractions(utilisateurRepository);
        verify(seanceRepository, never()).findAllById(any());
    }

    @Test
    void reorderProgrammes_missingProgramme_throwsException() {
        Seance s1 = new Seance(); s1.setId(10L); s1.setUtilisateur(owner);

        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(seanceRepository.findAllById(List.of(10L, 99L))).thenReturn(List.of(s1));

        assertThatThrownBy(() -> programmeService.reorderProgrammes("owner", List.of(10L, 99L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void reorderProgrammes_byUnauthorizedUser_throwsException() {
        Seance s1 = new Seance(); s1.setId(10L); s1.setUtilisateur(owner); s1.setDisplayOrder(0);

        when(utilisateurRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(seanceRepository.findAllById(List.of(10L))).thenReturn(List.of(s1));

        assertThatThrownBy(() -> programmeService.reorderProgrammes("other", List.of(10L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");

        assertThat(s1.getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void reorderProgrammes_byCoach_succeeds() {
        Utilisateur coach = Utilisateur.builder().id(3L).username("coach").role(Role.USER).build();
        owner.addCoach(coach);

        Seance s1 = new Seance(); s1.setId(10L); s1.setUtilisateur(owner); s1.setDisplayOrder(7);

        when(utilisateurRepository.findByUsername("coach")).thenReturn(Optional.of(coach));
        when(seanceRepository.findAllById(List.of(10L))).thenReturn(List.of(s1));

        programmeService.reorderProgrammes("coach", List.of(10L));

        assertThat(s1.getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void reorderProgrammes_byAdmin_succeeds() {
        Utilisateur admin = Utilisateur.builder().id(9L).username("admin").role(Role.ADMIN).build();
        Seance s1 = new Seance(); s1.setId(10L); s1.setUtilisateur(owner); s1.setDisplayOrder(3);
        Seance s2 = new Seance(); s2.setId(20L); s2.setUtilisateur(owner); s2.setDisplayOrder(4);

        when(utilisateurRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(seanceRepository.findAllById(List.of(20L, 10L))).thenReturn(List.of(s1, s2));

        programmeService.reorderProgrammes("admin", List.of(20L, 10L));

        assertThat(s2.getDisplayOrder()).isEqualTo(0);
        assertThat(s1.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void reorderProgrammes_mixedOwners_rejectsIfAnyUnauthorized() {
        Seance mine = new Seance(); mine.setId(10L); mine.setUtilisateur(owner); mine.setDisplayOrder(0);
        Seance theirs = new Seance(); theirs.setId(20L); theirs.setUtilisateur(otherUser); theirs.setDisplayOrder(1);

        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(seanceRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(mine, theirs));

        assertThatThrownBy(() -> programmeService.reorderProgrammes("owner", List.of(10L, 20L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    // --- getProgrammeById ---

    @Test
    void getProgrammeById_ownerAccess_returnsSeance() {
        Seance seance = new Seance();
        seance.setId(10L);
        seance.setUtilisateur(owner);

        when(seanceRepository.findById(10L)).thenReturn(Optional.of(seance));
        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

        Seance result = programmeService.getProgrammeById(10L, "owner");

        assertThat(result).isEqualTo(seance);
    }

    @Test
    void getProgrammeById_foreignPublicProfile_returnsSeance() {
        Seance seance = new Seance();
        seance.setId(10L);
        seance.setUtilisateur(owner); // owner is public

        when(seanceRepository.findById(10L)).thenReturn(Optional.of(seance));
        when(utilisateurRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));

        Seance result = programmeService.getProgrammeById(10L, "other");

        assertThat(result).isEqualTo(seance);
    }

    @Test
    void getProgrammeById_privateProfile_foreignUser_throwsException() {
        Utilisateur privateOwner = Utilisateur.builder()
                .id(5L).username("priv").role(Role.USER).isPublic(false).build();
        Seance seance = new Seance();
        seance.setId(10L);
        seance.setUtilisateur(privateOwner);

        when(seanceRepository.findById(10L)).thenReturn(Optional.of(seance));
        when(utilisateurRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> programmeService.getProgrammeById(10L, "other"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("private");
    }

    // --- deleteProgramme ---

    @Test
    void deleteProgramme_byOwner_deletes() {
        Seance seance = new Seance();
        seance.setId(10L);
        seance.setUtilisateur(owner);

        when(seanceRepository.findById(10L)).thenReturn(Optional.of(seance));
        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

        programmeService.deleteProgramme(10L, "owner");

        verify(seanceRepository).delete(seance);
    }

    @Test
    void deleteProgramme_byUnauthorized_throwsException() {
        Seance seance = new Seance();
        seance.setId(10L);
        seance.setUtilisateur(owner);

        when(seanceRepository.findById(10L)).thenReturn(Optional.of(seance));
        when(utilisateurRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> programmeService.deleteProgramme(10L, "other"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    // --- copyProgramme ---

    @Test
    void copyProgramme_fromPublicProfile_createsDeepCopy() {
        Exercice exo = new Exercice();
        exo.setNom("Bench");
        Serie serie = new Serie();
        serie.setPoids(80.0);
        serie.setNombreReps(5);
        exo.addSerie(serie);

        Seance source = new Seance();
        source.setId(1L);
        source.setTitre("Pull Day");
        source.setUtilisateur(owner); // owner is public
        source.addExercice(exo);

        when(utilisateurRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(seanceRepository.findById(1L)).thenReturn(Optional.of(source));

        programmeService.copyProgramme(1L, "other");

        verify(seanceRepository).save(argThat(copy ->
                copy.getTitre().contains("Copie") &&
                copy.getUtilisateur().equals(otherUser) &&
                !copy.getExercices().isEmpty()
        ));
    }

    @Test
    void copyProgramme_fromPrivateProfile_throwsException() {
        Seance source = new Seance();
        source.setId(1L);
        source.setUtilisateur(otherUser); // private (isPublic=false)

        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(seanceRepository.findById(1L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> programmeService.copyProgramme(1L, "owner"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("private");
    }

    @Test
    void copyProgramme_ownProgramme_alwaysAllowed() {
        Seance source = new Seance();
        source.setId(1L);
        source.setTitre("My Prog");
        source.setUtilisateur(owner);

        when(utilisateurRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(seanceRepository.findById(1L)).thenReturn(Optional.of(source));

        programmeService.copyProgramme(1L, "owner");

        verify(seanceRepository).save(any());
    }
}
