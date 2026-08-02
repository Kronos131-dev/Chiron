package com.kronos.chiron.utilisateur.service.impl;

import org.mockito.Spy;

import java.time.ZoneId;

import java.time.Clock;

import com.kronos.chiron.auth.service.EmailService;

import com.kronos.chiron.coach.agent.ChironAgentRouter;
import com.kronos.chiron.utilisateur.dto.AiProviderDto;
import com.kronos.chiron.utilisateur.dto.TrainingPrefsDto;
import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.auth.model.PasswordResetToken;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.auth.persistence.PasswordResetTokenRepository;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    private static final String USERNAME = "kronos";

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private JwtService jwtService;
    @Mock
    private ChironAgentRouter chironAgentRouter;

    @Spy
    private Clock clock = Clock.system(ZoneId.of("Europe/Paris"));

    @InjectMocks
    private SettingsServiceImpl settingsService;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = new Utilisateur();
        user.setId(1L);
        user.setUsername(USERNAME);
        user.setPassword("encoded-current");
        ReflectionTestUtils.setField(settingsService, "frontendUrl", "https://chiron.app");
    }

    private void givenUserExists() {
        when(utilisateurRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
    }

    private void givenUserMissing() {
        when(utilisateurRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
    }

    @Test
    void getTrainingPrefs_returnsTheStoredPreferences() {
        user.setPoidsHaltereParImplement(false);
        user.setPoidsMachineParCote(true);
        givenUserExists();

        TrainingPrefsDto prefs = settingsService.getTrainingPrefs(USERNAME);

        assertThat(prefs.halteresParImplement()).isFalse();
        assertThat(prefs.machineParCote()).isTrue();
    }

    @Test
    void getTrainingPrefs_unknownUser_throwsNoSuchElement() {
        givenUserMissing();

        assertThatThrownBy(() -> settingsService.getTrainingPrefs(USERNAME))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateTrainingPrefs_persistsBothFlags() {
        givenUserExists();

        settingsService.updateTrainingPrefs(USERNAME, false, true);

        assertThat(user.isPoidsHaltereParImplement()).isFalse();
        assertThat(user.isPoidsMachineParCote()).isTrue();
        verify(utilisateurRepository).save(user);
    }

    @Test
    void getAiProvider_reportsUserChoiceAndGeminiAvailability() {
        user.setAiProvider(AiProvider.GEMINI);
        givenUserExists();
        when(chironAgentRouter.geminiAvailable()).thenReturn(true);

        AiProviderDto dto = settingsService.getAiProvider(USERNAME);

        assertThat(dto.provider()).isEqualTo(AiProvider.GEMINI);
        assertThat(dto.geminiAvailable()).isTrue();
    }

    @Test
    void updateAiProvider_setsTheRequestedProvider() {
        givenUserExists();

        settingsService.updateAiProvider(USERNAME, AiProvider.GEMINI);

        assertThat(user.getAiProvider()).isEqualTo(AiProvider.GEMINI);
        verify(utilisateurRepository).save(user);
    }

    @Test
    void updateAiProvider_nullProvider_fallsBackToMistral() {
        givenUserExists();

        settingsService.updateAiProvider(USERNAME, null);

        assertThat(user.getAiProvider()).isEqualTo(AiProvider.MISTRAL);
    }

    @Test
    void changePassword_correctCurrentPassword_storesTheEncodedNewOne() {
        givenUserExists();
        when(passwordEncoder.matches("current", "encoded-current")).thenReturn(true);
        when(passwordEncoder.encode("nouveau")).thenReturn("encoded-nouveau");

        settingsService.changePassword(USERNAME, "current", "nouveau");

        assertThat(user.getPassword()).isEqualTo("encoded-nouveau");
        verify(utilisateurRepository).save(user);
    }

    @Test
    void changePassword_wrongCurrentPassword_isRejectedAndNothingIsSaved() {
        givenUserExists();
        when(passwordEncoder.matches("faux", "encoded-current")).thenReturn(false);

        assertThatThrownBy(() -> settingsService.changePassword(USERNAME, "faux", "nouveau"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void changeEmail_unusedAddress_isStored() {
        givenUserExists();
        when(utilisateurRepository.findByEmail("neuf@chiron.app")).thenReturn(Optional.empty());

        settingsService.changeEmail(USERNAME, "neuf@chiron.app");

        assertThat(user.getEmail()).isEqualTo("neuf@chiron.app");
    }

    @Test
    void changeEmail_addressAlreadyTaken_isRejected() {
        givenUserExists();
        when(utilisateurRepository.findByEmail("pris@chiron.app"))
                .thenReturn(Optional.of(new Utilisateur()));

        assertThatThrownBy(() -> settingsService.changeEmail(USERNAME, "pris@chiron.app"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void changeIdentity_trimsBothNames() {
        givenUserExists();

        settingsService.changeIdentity(USERNAME, "  Yvain  ", "  Dupont ");

        assertThat(user.getPrenom()).isEqualTo("Yvain");
        assertThat(user.getNom()).isEqualTo("Dupont");
    }

    @Test
    void changeIdentity_blankNames_areStoredAsNull() {
        givenUserExists();

        settingsService.changeIdentity(USERNAME, "   ", "");

        assertThat(user.getPrenom()).isNull();
        assertThat(user.getNom()).isNull();
    }

    @Test
    void changeIdentity_nullNames_areStoredAsNull() {
        givenUserExists();

        settingsService.changeIdentity(USERNAME, null, null);

        assertThat(user.getPrenom()).isNull();
        assertThat(user.getNom()).isNull();
    }

    @Test
    void changeUsername_availableName_isStoredAndANewTokenIsIssued() {
        givenUserExists();
        when(utilisateurRepository.findByUsernameIgnoreCase("chiron")).thenReturn(Optional.empty());
        when(jwtService.generateToken(user)).thenReturn("jwt");

        assertThat(settingsService.changeUsername(USERNAME, "chiron")).isEqualTo("jwt");
        assertThat(user.getUsername()).isEqualTo("chiron");
    }

    @Test
    void changeUsername_takenByAnotherUser_isRejected() {
        givenUserExists();
        Utilisateur other = new Utilisateur();
        other.setId(2L);
        when(utilisateurRepository.findByUsernameIgnoreCase("chiron")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> settingsService.changeUsername(USERNAME, "chiron"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void changeUsername_onlyDifferingByCase_isAllowedForTheSameUser() {
        givenUserExists();
        when(utilisateurRepository.findByUsernameIgnoreCase("KRONOS")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt");

        settingsService.changeUsername(USERNAME, "KRONOS");

        assertThat(user.getUsername()).isEqualTo("KRONOS");
    }

    @Test
    void forgotPassword_unknownEmail_doesNothingAndDoesNotRevealIt() {
        when(utilisateurRepository.findByEmail("inconnu@chiron.app")).thenReturn(Optional.empty());

        settingsService.forgotPassword("inconnu@chiron.app");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void forgotPassword_knownEmail_invalidatesPreviousTokensAndIssuesAFreshOne() {
        user.setEmail("yvain@chiron.app");
        when(utilisateurRepository.findByEmail("yvain@chiron.app")).thenReturn(Optional.of(user));

        settingsService.forgotPassword("yvain@chiron.app");

        verify(tokenRepository).deleteByUtilisateurAndUsedFalse(user);
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUtilisateur()).isSameAs(user);
        assertThat(captor.getValue().getToken()).isNotBlank();
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
    }

    @Test
    void forgotPassword_sendsALinkBuiltOnTheFrontendUrl() {
        user.setEmail("yvain@chiron.app");
        when(utilisateurRepository.findByEmail("yvain@chiron.app")).thenReturn(Optional.of(user));

        settingsService.forgotPassword("yvain@chiron.app");

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq("yvain@chiron.app"),
                link.capture());
        assertThat(link.getValue()).startsWith("https://chiron.app/reset-password?token=");
    }

    @Test
    void forgotPassword_frontendUrlPointingAtAScreen_isStrippedBackToTheOrigin() {
        ReflectionTestUtils.setField(settingsService, "frontendUrl", "https://chiron.app/chat/");
        user.setEmail("yvain@chiron.app");
        when(utilisateurRepository.findByEmail("yvain@chiron.app")).thenReturn(Optional.of(user));

        settingsService.forgotPassword("yvain@chiron.app");

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(anyString(), link.capture());
        assertThat(link.getValue()).startsWith("https://chiron.app/reset-password?token=");
    }

    @Test
    void forgotPassword_emailSendingFails_theTokenIsStillPersisted() {
        user.setEmail("yvain@chiron.app");
        when(utilisateurRepository.findByEmail("yvain@chiron.app")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendPasswordResetEmail(anyString(), anyString());

        settingsService.forgotPassword("yvain@chiron.app");

        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPassword_validToken_setsTheNewPasswordAndBurnsTheToken() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("tok").utilisateur(user).used(false)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(tokenRepository.findByToken("tok")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("nouveau")).thenReturn("encoded-nouveau");

        settingsService.resetPassword("tok", "nouveau");

        assertThat(user.getPassword()).isEqualTo("encoded-nouveau");
        assertThat(token.getUsed()).isTrue();
        verify(tokenRepository).save(token);
    }

    @Test
    void resetPassword_unknownToken_isRejected() {
        when(tokenRepository.findByToken("tok")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settingsService.resetPassword("tok", "nouveau"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPassword_tokenAlreadyUsed_isRejected() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("tok").utilisateur(user).used(true)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(tokenRepository.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> settingsService.resetPassword("tok", "nouveau"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void resetPassword_expiredToken_isRejected() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("tok").utilisateur(user).used(false)
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(tokenRepository.findByToken("tok")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> settingsService.resetPassword("tok", "nouveau"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(utilisateurRepository, never()).save(any());
    }
}
