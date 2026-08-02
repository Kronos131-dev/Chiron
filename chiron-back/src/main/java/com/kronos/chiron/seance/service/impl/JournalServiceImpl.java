package com.kronos.chiron.seance.service.impl;

import com.kronos.chiron.seance.service.JournalService;

import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.mapper.SeanceMapper;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JournalServiceImpl implements JournalService {

    private final SeanceRepository seanceRepository;
    private final SeanceMapper seanceMapper;
    private final Clock clock;

    @Override
    public List<SeanceDto> getSeancesForCurrentWeek(Long utilisateurId) {
        int currentWeek = getCurrentWeekNumber();
        List<Seance> seances = seanceRepository.findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(utilisateurId,
                currentWeek);

        return seances.stream()
                .map(seanceMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public int getCurrentWeekNumber() {
        WeekFields weekFields = WeekFields.of(Locale.FRANCE);
        return LocalDate.now(clock).get(weekFields.weekOfWeekBasedYear());
    }
}
