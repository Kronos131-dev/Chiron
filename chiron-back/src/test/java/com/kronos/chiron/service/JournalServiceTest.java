package com.kronos.chiron.service;

import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.mapper.SeanceMapper;
import com.kronos.chiron.repository.SeanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    @Mock private SeanceRepository seanceRepository;
    @Mock private SeanceMapper seanceMapper;

    @InjectMocks
    private JournalService journalService;

    @Test
    void getCurrentWeekNumber_matchesFrenchLocale() {
        int expected = LocalDate.now().get(WeekFields.of(Locale.FRANCE).weekOfWeekBasedYear());
        assertThat(journalService.getCurrentWeekNumber()).isEqualTo(expected);
    }

    @Test
    void getSeancesForCurrentWeek_queriesWithCorrectWeekAndUser() {
        int currentWeek = journalService.getCurrentWeekNumber();
        when(seanceRepository.findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(1L, currentWeek))
                .thenReturn(List.of());

        List<SeanceDto> result = journalService.getSeancesForCurrentWeek(1L);

        assertThat(result).isEmpty();
        verify(seanceRepository).findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(1L, currentWeek);
    }

    @Test
    void getSeancesForCurrentWeek_mapsEachSeance() {
        int currentWeek = journalService.getCurrentWeekNumber();
        Seance seance = new Seance();
        seance.setId(10L);
        SeanceDto dto = new SeanceDto(10L, "Push", null, null, currentWeek, true, null, List.of());

        when(seanceRepository.findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(2L, currentWeek))
                .thenReturn(List.of(seance));
        when(seanceMapper.toDto(seance)).thenReturn(dto);

        List<SeanceDto> result = journalService.getSeancesForCurrentWeek(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
    }

    @Test
    void getSeancesForCurrentWeek_multipleSeances_returnsAll() {
        int currentWeek = journalService.getCurrentWeekNumber();
        Seance s1 = new Seance(); s1.setId(1L);
        Seance s2 = new Seance(); s2.setId(2L);
        SeanceDto d1 = new SeanceDto(1L, "A", null, null, currentWeek, true, null, List.of());
        SeanceDto d2 = new SeanceDto(2L, "B", null, null, currentWeek, true, null, List.of());

        when(seanceRepository.findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(3L, currentWeek))
                .thenReturn(List.of(s1, s2));
        when(seanceMapper.toDto(s1)).thenReturn(d1);
        when(seanceMapper.toDto(s2)).thenReturn(d2);

        List<SeanceDto> result = journalService.getSeancesForCurrentWeek(3L);

        assertThat(result).hasSize(2);
    }
}
