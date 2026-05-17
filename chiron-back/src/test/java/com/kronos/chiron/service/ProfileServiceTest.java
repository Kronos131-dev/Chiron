package com.kronos.chiron.service;

import com.kronos.chiron.dto.PerformanceSummaryDto;
import com.kronos.chiron.dto.ProfileDto;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.entity.Utilisateur;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private SeanceRepository seanceRepository;
    @Mock private PerformanceService performanceService;

    @InjectMocks
    private ProfileService profileService;

    private Utilisateur publicUser;
    private Utilisateur privateUser;
    private Utilisateur requestUser;
    private PerformanceSummaryDto emptyPerf;

    @BeforeEach
    void setUp() {
        publicUser = Utilisateur.builder()
                .id(1L).username("public").isPublic(true).role(Role.USER).build();
        privateUser = Utilisateur.builder()
                .id(2L).username("private").isPublic(false).role(Role.USER).build();
        requestUser = Utilisateur.builder()
                .id(3L).username("requester").isPublic(false).role(Role.USER).build();

        emptyPerf = PerformanceSummaryDto.builder()
                .overallTier("Éphèbe").overallTierLevel(1).build();

        when(seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(any()))
                .thenReturn(List.of());
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(any()))
                .thenReturn(List.of());
        when(seanceRepository.countTotalSeriesForUserSince(any(), any())).thenReturn(0);
        when(performanceService.getSummary(any())).thenReturn(emptyPerf);
    }

    // --- getProfile ---

    @Test
    void getProfile_publicUser_returnsProfile() {
        when(utilisateurRepository.findByUsername("public")).thenReturn(Optional.of(publicUser));
        when(utilisateurRepository.findByUsername("requester")).thenReturn(Optional.of(requestUser));

        ProfileDto dto = profileService.getProfile("public", "requester");

        assertThat(dto.getUsername()).isEqualTo("public");
        assertThat(dto.getIsPublic()).isTrue();
    }

    @Test
    void getProfile_ownProfile_alwaysAccessible() {
        when(utilisateurRepository.findByUsername("private")).thenReturn(Optional.of(privateUser));

        ProfileDto dto = profileService.getProfile("private", "private");

        assertThat(dto.getUsername()).isEqualTo("private");
    }

    @Test
    void getProfile_privateUser_foreignRequester_throwsException() {
        when(utilisateurRepository.findByUsername("private")).thenReturn(Optional.of(privateUser));
        when(utilisateurRepository.findByUsername("requester")).thenReturn(Optional.of(requestUser));

        assertThatThrownBy(() -> profileService.getProfile("private", "requester"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("private");
    }

    @Test
    void getProfile_adminRequester_bypassesPrivacy() {
        Utilisateur admin = Utilisateur.builder()
                .id(99L).username("kronos").role(Role.ADMIN).build();
        when(utilisateurRepository.findByUsername("private")).thenReturn(Optional.of(privateUser));
        when(utilisateurRepository.findByUsername("kronos")).thenReturn(Optional.of(admin));

        ProfileDto dto = profileService.getProfile("private", "kronos");

        assertThat(dto.getUsername()).isEqualTo("private");
    }

    @Test
    void getProfile_coachRequester_bypassesPrivacy() {
        Utilisateur coach = Utilisateur.builder()
                .id(5L).username("coach").isPublic(false).role(Role.USER).build();
        privateUser.addCoach(coach);

        when(utilisateurRepository.findByUsername("private")).thenReturn(Optional.of(privateUser));
        when(utilisateurRepository.findByUsername("coach")).thenReturn(Optional.of(coach));

        ProfileDto dto = profileService.getProfile("private", "coach");

        assertThat(dto.getUsername()).isEqualTo("private");
    }

    @Test
    void getProfile_userNotFound_throwsException() {
        when(utilisateurRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile("ghost", "someone"))
                .isInstanceOf(RuntimeException.class);
    }

    // --- calculateRank boundaries ---

    @Test
    void getProfile_noSeries_rankIsCitoyen() {
        when(utilisateurRepository.findByUsername("public")).thenReturn(Optional.of(publicUser));
        when(utilisateurRepository.findByUsername("public")).thenReturn(Optional.of(publicUser));

        ProfileDto dto = profileService.getProfile("public", "public");
        assertThat(dto.getRank()).isEqualTo("Citoyen");
    }

    @Test
    void getProfile_50Series_rankIsAthlete() {
        when(seanceRepository.countTotalSeriesForUserSince(any(), any())).thenReturn(50);
        when(utilisateurRepository.findByUsername("public")).thenReturn(Optional.of(publicUser));

        ProfileDto dto = profileService.getProfile("public", "public");
        assertThat(dto.getRank()).isEqualTo("Athlète");
    }

    @Test
    void getProfile_100Series_rankIsSpartiate() {
        when(seanceRepository.countTotalSeriesForUserSince(any(), any())).thenReturn(100);
        when(utilisateurRepository.findByUsername("public")).thenReturn(Optional.of(publicUser));

        ProfileDto dto = profileService.getProfile("public", "public");
        assertThat(dto.getRank()).isEqualTo("Spartiate");
    }

    @Test
    void getProfile_150Series_rankIsHeros() {
        when(seanceRepository.countTotalSeriesForUserSince(any(), any())).thenReturn(150);
        when(utilisateurRepository.findByUsername("public")).thenReturn(Optional.of(publicUser));

        ProfileDto dto = profileService.getProfile("public", "public");
        assertThat(dto.getRank()).isEqualTo("Héros");
    }

    @Test
    void getProfile_200Series_rankIsOlympien() {
        when(seanceRepository.countTotalSeriesForUserSince(any(), any())).thenReturn(200);
        when(utilisateurRepository.findByUsername("public")).thenReturn(Optional.of(publicUser));

        ProfileDto dto = profileService.getProfile("public", "public");
        assertThat(dto.getRank()).isEqualTo("Olympien");
    }

    // --- updateVisibility ---

    @Test
    void updateVisibility_setsFlag() {
        when(utilisateurRepository.findByUsername("alice")).thenReturn(
                Optional.of(Utilisateur.builder().username("alice").build()));

        profileService.updateVisibility("alice", true);

        verify(utilisateurRepository).save(argThat(u -> u.getIsPublic() == Boolean.TRUE));
    }

    // --- addCoach / removeCoach ---

    @Test
    void addCoach_createsRelationship() {
        Utilisateur student = Utilisateur.builder().id(10L).username("student").build();
        Utilisateur coach = Utilisateur.builder().id(11L).username("coach").build();
        when(utilisateurRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(utilisateurRepository.findByUsername("coach")).thenReturn(Optional.of(coach));

        profileService.addCoach("student", "coach");

        assertThat(student.getCoaches()).contains(coach);
        verify(utilisateurRepository).save(student);
    }

    @Test
    void addCoach_coachNotFound_throwsException() {
        when(utilisateurRepository.findByUsername("student")).thenReturn(
                Optional.of(Utilisateur.builder().username("student").build()));
        when(utilisateurRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.addCoach("student", "nobody"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void removeCoach_removesRelationship() {
        Utilisateur student = Utilisateur.builder().id(10L).username("student").build();
        Utilisateur coach = Utilisateur.builder().id(11L).username("coach").build();
        student.addCoach(coach);

        when(utilisateurRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(utilisateurRepository.findByUsername("coach")).thenReturn(Optional.of(coach));

        profileService.removeCoach("student", "coach");

        assertThat(student.getCoaches()).doesNotContain(coach);
    }

    // --- deleteProfile ---

    @Test
    void deleteProfile_ownProfile_deletesUser() {
        Utilisateur user = Utilisateur.builder().id(1L).username("alice").role(Role.USER).build();
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        profileService.deleteProfile("alice", "alice");

        verify(utilisateurRepository).delete(user);
    }

    @Test
    void deleteProfile_adminDeletesOther_succeeds() {
        Utilisateur admin = Utilisateur.builder().id(1L).username("kronos").role(Role.ADMIN).build();
        Utilisateur target = Utilisateur.builder().id(2L).username("victim").role(Role.USER).build();
        when(utilisateurRepository.findByUsername("victim")).thenReturn(Optional.of(target));
        when(utilisateurRepository.findByUsername("kronos")).thenReturn(Optional.of(admin));

        profileService.deleteProfile("victim", "kronos");

        verify(utilisateurRepository).delete(target);
    }

    @Test
    void deleteProfile_unauthorizedUser_throwsException() {
        Utilisateur target = Utilisateur.builder().id(2L).username("victim").role(Role.USER).build();
        Utilisateur requester = Utilisateur.builder().id(3L).username("other").role(Role.USER).build();
        when(utilisateurRepository.findByUsername("victim")).thenReturn(Optional.of(target));
        when(utilisateurRepository.findByUsername("other")).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> profileService.deleteProfile("victim", "other"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    // --- getAllProfiles ---

    @Test
    void getAllProfiles_adminSeesAll() {
        Utilisateur admin = Utilisateur.builder().id(1L).username("kronos").role(Role.ADMIN).build();
        when(utilisateurRepository.findByUsername("kronos")).thenReturn(Optional.of(admin));
        when(utilisateurRepository.findAll()).thenReturn(List.of(publicUser, privateUser));

        List<ProfileDto> results = profileService.getAllProfiles("kronos");

        assertThat(results).hasSize(2);
    }

    @Test
    void getAllProfiles_normalUser_seesOnlyPublic() {
        when(utilisateurRepository.findByUsername("requester")).thenReturn(Optional.of(requestUser));
        when(utilisateurRepository.findAll()).thenReturn(List.of(publicUser, privateUser));

        List<ProfileDto> results = profileService.getAllProfiles("requester");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("public");
    }

    @Test
    void getAllProfiles_coachSeesPrivateUser() {
        Utilisateur coach = Utilisateur.builder().id(5L).username("coach").isPublic(false).role(Role.USER).build();
        privateUser.addCoach(coach);

        when(utilisateurRepository.findByUsername("coach")).thenReturn(Optional.of(coach));
        when(utilisateurRepository.findAll()).thenReturn(List.of(publicUser, privateUser));

        List<ProfileDto> results = profileService.getAllProfiles("coach");

        assertThat(results).hasSize(2);
    }

    // --- searchProfiles ---

    @Test
    void searchProfiles_adminSeeAll() {
        Utilisateur admin = Utilisateur.builder().id(1L).username("kronos").role(Role.ADMIN).build();
        when(utilisateurRepository.findByUsername("kronos")).thenReturn(Optional.of(admin));
        when(utilisateurRepository.findByUsernameContainingIgnoreCase("a")).thenReturn(
                List.of(publicUser, privateUser));

        List<ProfileDto> results = profileService.searchProfiles("a", "kronos");

        assertThat(results).hasSize(2);
    }

    @Test
    void searchProfiles_normalUser_seesOnlyPublic() {
        when(utilisateurRepository.findByUsername("requester")).thenReturn(Optional.of(requestUser));
        when(utilisateurRepository.findByUsernameContainingIgnoreCase("u")).thenReturn(
                List.of(publicUser, privateUser));

        List<ProfileDto> results = profileService.searchProfiles("u", "requester");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("public");
    }
}
