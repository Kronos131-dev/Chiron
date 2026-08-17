package com.kronos.chiron.fitbit.service.impl;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.dto.FitbitDashboardDto;
import com.kronos.chiron.fitbit.dto.FitbitLinkStatus;
import com.kronos.chiron.fitbit.service.FitbitAuthSessionStore;
import com.kronos.chiron.fitbit.service.FitbitService;

import org.mockito.Spy;

import java.time.ZoneId;

import java.time.Clock;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.core.security.TokenCipherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FitbitServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private FitbitClient fitbitClient;
    @Mock
    private TokenCipherService tokenCipher;
    @Mock
    private FitbitAuthSessionStore authSessionStore;

    @Spy
    private Clock clock = Clock.system(ZoneId.of("Europe/Paris"));

    @InjectMocks
    private FitbitServiceImpl fitbitService;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(1L).username("athlete").build();
        when(utilisateurRepository.findByUsername("athlete")).thenReturn(Optional.of(user));
    }

    @Test
    void getValidToken_returnsStoredTokenWhenStillValid() {
        user.setFitbitAccessTokenEncrypted("enc-access");
        user.setFitbitRefreshTokenEncrypted("enc-refresh");
        user.setFitbitTokenExpiresAt(LocalDateTime.now(clock).plusHours(2));
        when(tokenCipher.decrypt("enc-access")).thenReturn("plain-access");

        assertThat(fitbitService.getValidToken("athlete")).isEqualTo("plain-access");
        verify(fitbitClient, never()).refresh(anyString());
    }

    @Test
    void getValidToken_refreshesWhenExpired_andPersistsRotatedTokens() {
        user.setFitbitAccessTokenEncrypted("old-access");
        user.setFitbitRefreshTokenEncrypted("enc-old-refresh");
        user.setFitbitTokenExpiresAt(LocalDateTime.now(clock).minusMinutes(5));
        when(tokenCipher.decrypt("enc-old-refresh")).thenReturn("old-refresh");
        when(tokenCipher.encrypt("new-access")).thenReturn("enc-new-access");
        when(tokenCipher.encrypt("new-refresh")).thenReturn("enc-new-refresh");
        when(fitbitClient.refresh("old-refresh")).thenReturn(
                new FitbitClient.TokenResponse("new-access", "new-refresh", 28800L, "activity", "FBUSER"));

        String token = fitbitService.getValidToken("athlete");

        assertThat(token).isEqualTo("new-access");
        assertThat(user.getFitbitAccessTokenEncrypted()).isEqualTo("enc-new-access");
        assertThat(user.getFitbitRefreshTokenEncrypted()).isEqualTo("enc-new-refresh");
        verify(utilisateurRepository).save(user);
    }

    @Test
    void getValidToken_clearsLinkWhenRefreshRejected() {
        user.setFitbitAccessTokenEncrypted("old-access");
        user.setFitbitRefreshTokenEncrypted("enc-old-refresh");
        user.setFitbitTokenExpiresAt(LocalDateTime.now(clock).minusMinutes(5));
        when(tokenCipher.decrypt("enc-old-refresh")).thenReturn("old-refresh");
        when(fitbitClient.refresh("old-refresh"))
                .thenThrow(new FitbitClient.FitbitUnauthorizedException("refused"));

        assertThatThrownBy(() -> fitbitService.getValidToken("athlete"))
                .isInstanceOf(FitbitService.ExpiredException.class);
        assertThat(user.getFitbitAccessTokenEncrypted()).isNull();
        assertThat(user.getFitbitRefreshTokenEncrypted()).isNull();
    }

    @Test
    void getValidToken_throwsNotLinkedWhenNoTokens() {
        assertThatThrownBy(() -> fitbitService.getValidToken("athlete"))
                .isInstanceOf(FitbitService.NotLinkedException.class);
    }

    @Test
    void handleCallback_unknownState_throwsInvalidState() {
        when(authSessionStore.consume("bad-state")).thenReturn(null);

        assertThatThrownBy(() -> fitbitService.handleCallback("code", "bad-state"))
                .isInstanceOf(FitbitService.InvalidStateException.class);
    }

    @Test
    void getStatus_reportsLinkedWhenTokensPresent() {
        user.setFitbitRefreshTokenEncrypted("enc-refresh");
        user.setFitbitUserId("FBUSER");

        FitbitLinkStatus status = fitbitService.getStatus("athlete");

        assertThat(status.linked()).isTrue();
        assertThat(status.fitbitUserId()).isEqualTo("FBUSER");
    }

    @Test
    void getDashboard_dataCallForbidden_keepsLinkAndReportsUnavailable() {
        user.setFitbitAccessTokenEncrypted("enc-access");
        user.setFitbitRefreshTokenEncrypted("enc-refresh");
        user.setFitbitTokenExpiresAt(LocalDateTime.now(clock).plusHours(2));
        when(tokenCipher.decrypt("enc-access")).thenReturn("plain-access");
        when(fitbitClient.rollUpDailySteps(eq("plain-access"), any(), any()))
                .thenThrow(new FitbitClient.FitbitUnavailableException("Accès Google Health refusé (403)"));

        FitbitDashboardDto dto = fitbitService.getDashboard("athlete", 7);

        assertThat(dto.linked()).isTrue();
        assertThat(dto.needsReconnect()).isFalse();
        assertThat(dto.dataAvailable()).isFalse();
        // Cœur du bug : un échec d'appel data ne doit PAS effacer les tokens OAuth.
        assertThat(user.getFitbitAccessTokenEncrypted()).isEqualTo("enc-access");
        assertThat(user.getFitbitRefreshTokenEncrypted()).isEqualTo("enc-refresh");
    }

    @Test
    void getDashboard_dataCallUnauthorized_keepsLink() {
        user.setFitbitAccessTokenEncrypted("enc-access");
        user.setFitbitRefreshTokenEncrypted("enc-refresh");
        user.setFitbitTokenExpiresAt(LocalDateTime.now(clock).plusHours(2));
        when(tokenCipher.decrypt("enc-access")).thenReturn("plain-access");
        when(fitbitClient.rollUpDailySteps(eq("plain-access"), any(), any()))
                .thenThrow(new FitbitClient.FitbitUnauthorizedException("Token Google Health rejeté (401)"));

        FitbitDashboardDto dto = fitbitService.getDashboard("athlete", 7);

        assertThat(dto.needsReconnect()).isFalse();
        assertThat(dto.dataAvailable()).isFalse();
        // Un 401 sur un appel data ne révoque pas le grant : le refresh token survit.
        assertThat(user.getFitbitRefreshTokenEncrypted()).isEqualTo("enc-refresh");
    }
}
