package com.kronos.chiron.nutrition;

import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionDao;
import com.kronos.chiron.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NutritionServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private OlympusNutritionDao olympusDao;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private NutritionService nutritionService;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(1L).username("athlete").build();
        when(utilisateurRepository.findByUsername("athlete")).thenReturn(Optional.of(user));
    }

    @Test
    void link_validCredentials_storesOlympusUserId() {
        when(olympusDao.findAuthByEmail("user@olympus.io"))
                .thenReturn(Optional.of(new OlympusNutritionDao.AuthRow(42L, "$2a$hash")));
        when(passwordEncoder.matches("secret", "$2a$hash")).thenReturn(true);

        NutritionLinkStatus status = nutritionService.link("athlete", "user@olympus.io", "secret");

        assertThat(status.linked()).isTrue();
        assertThat(status.expired()).isFalse();
        assertThat(user.getOlympusUserId()).isEqualTo(42L);
        assertThat(user.getOlympusUsername()).isEqualTo("user@olympus.io");
        verify(utilisateurRepository).save(user);
    }

    @Test
    void link_wrongPassword_throwsAndStoresNothing() {
        when(olympusDao.findAuthByEmail("user@olympus.io"))
                .thenReturn(Optional.of(new OlympusNutritionDao.AuthRow(42L, "$2a$hash")));
        when(passwordEncoder.matches("bad", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> nutritionService.link("athlete", "user@olympus.io", "bad"))
                .isInstanceOf(NutritionService.InvalidCredentialsException.class);

        assertThat(user.getOlympusUserId()).isNull();
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void link_unknownEmail_throws() {
        when(olympusDao.findAuthByEmail("ghost@olympus.io")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nutritionService.link("athlete", "ghost@olympus.io", "secret"))
                .isInstanceOf(NutritionService.InvalidCredentialsException.class);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void getOlympusUserId_returnsStoredId() {
        user.setOlympusUserId(7L);
        assertThat(nutritionService.getOlympusUserId("athlete")).contains(7L);
    }

    @Test
    void getOlympusUserId_notLinked_empty() {
        assertThat(nutritionService.getOlympusUserId("athlete")).isEmpty();
    }

    @Test
    void unlink_clearsLink() {
        user.setOlympusUserId(7L);
        user.setOlympusUsername("user@olympus.io");

        nutritionService.unlink("athlete");

        assertThat(user.getOlympusUserId()).isNull();
        assertThat(user.getOlympusUsername()).isNull();
        verify(utilisateurRepository).save(user);
    }
}
