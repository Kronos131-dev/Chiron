package com.kronos.chiron.coach.service.impl;

import com.kronos.chiron.coach.service.AiUsageService;

import org.mockito.Spy;

import java.time.ZoneId;

import java.time.Clock;

import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiUsageServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Spy
    private Clock clock = Clock.system(ZoneId.of("Europe/Paris"));

    @InjectMocks
    private AiUsageServiceImpl aiUsageService;

    private static Utilisateur user(AiProvider provider, Role role, LocalDate callDate, int callCount) {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setAiProvider(provider);
        u.setRole(role);
        u.setGeminiCallDate(callDate);
        u.setGeminiCallCount(callCount);
        return u;
    }

    @Test
    void resolveProvider_userPrefersMistral_returnsMistralWithoutTouchingQuota() {
        Utilisateur user = user(AiProvider.MISTRAL, Role.USER, LocalDate.now(), 0);

        assertThat(aiUsageService.resolveProvider(user)).isEqualTo(AiProvider.MISTRAL);
        verify(utilisateurRepository, never()).findById(1L);
    }

    @Test
    void resolveProvider_admin_returnsGeminiWithoutConsumingQuota() {
        Utilisateur admin = user(AiProvider.GEMINI, Role.ADMIN, LocalDate.now(), 99);

        assertThat(aiUsageService.resolveProvider(admin)).isEqualTo(AiProvider.GEMINI);
        verify(utilisateurRepository, never()).findById(1L);
        verify(utilisateurRepository, never()).save(admin);
    }

    @Test
    void resolveProvider_underDailyLimit_returnsGeminiAndIncrementsCount() {
        Utilisateur user = user(AiProvider.GEMINI, Role.USER, LocalDate.now(), 2);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(aiUsageService.resolveProvider(user)).isEqualTo(AiProvider.GEMINI);
        assertThat(user.getGeminiCallCount()).isEqualTo(3);
        verify(utilisateurRepository).save(user);
    }

    @Test
    void resolveProvider_lastCallOfTheDay_stillReturnsGemini() {
        Utilisateur user = user(AiProvider.GEMINI, Role.USER, LocalDate.now(),
                AiUsageService.DAILY_GEMINI_LIMIT - 1);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(aiUsageService.resolveProvider(user)).isEqualTo(AiProvider.GEMINI);
        assertThat(user.getGeminiCallCount()).isEqualTo(AiUsageService.DAILY_GEMINI_LIMIT);
    }

    @Test
    void resolveProvider_dailyLimitReached_downgradesToMistralSilently() {
        Utilisateur user = user(AiProvider.GEMINI, Role.USER, LocalDate.now(),
                AiUsageService.DAILY_GEMINI_LIMIT);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(aiUsageService.resolveProvider(user)).isEqualTo(AiProvider.MISTRAL);
        assertThat(user.getGeminiCallCount()).isEqualTo(AiUsageService.DAILY_GEMINI_LIMIT);
        verify(utilisateurRepository, never()).save(user);
    }

    @Test
    void resolveProvider_quotaFromAPreviousDay_isResetBeforeBeingChecked() {
        Utilisateur user = user(AiProvider.GEMINI, Role.USER, LocalDate.now().minusDays(1),
                AiUsageService.DAILY_GEMINI_LIMIT);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(aiUsageService.resolveProvider(user)).isEqualTo(AiProvider.GEMINI);
        assertThat(user.getGeminiCallDate()).isEqualTo(LocalDate.now());
        assertThat(user.getGeminiCallCount()).isEqualTo(1);
    }

    @Test
    void resolveProvider_neverCalledGeminiBefore_startsCountAtOne() {
        Utilisateur user = user(AiProvider.GEMINI, Role.USER, null, 0);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(aiUsageService.resolveProvider(user)).isEqualTo(AiProvider.GEMINI);
        assertThat(user.getGeminiCallDate()).isEqualTo(LocalDate.now());
        assertThat(user.getGeminiCallCount()).isEqualTo(1);
    }

    @Test
    void resolveProvider_userNoLongerInDatabase_throwsNoSuchElement() {
        Utilisateur user = user(AiProvider.GEMINI, Role.USER, LocalDate.now(), 0);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiUsageService.resolveProvider(user))
                .isInstanceOf(NoSuchElementException.class);
    }
}
