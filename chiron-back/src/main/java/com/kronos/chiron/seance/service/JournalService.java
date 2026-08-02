package com.kronos.chiron.seance.service;

import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.mapper.SeanceMapper;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class JournalService {

    private final SeanceRepository seanceRepository;
    private final SeanceMapper seanceMapper;

    public JournalService(SeanceRepository seanceRepository, SeanceMapper seanceMapper) {
        this.seanceRepository = seanceRepository;
        this.seanceMapper = seanceMapper;
    }

    public List<SeanceDto> getSeancesForCurrentWeek(Long utilisateurId) {
        int currentWeek = getCurrentWeekNumber();
        List<Seance> seances = seanceRepository.findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(utilisateurId, currentWeek);

        return seances.stream()
                .map(seanceMapper::toDto)
                .collect(Collectors.toList());
    }

    public int getCurrentWeekNumber() {
        WeekFields weekFields = WeekFields.of(Locale.FRANCE);
        return LocalDate.now().get(weekFields.weekOfWeekBasedYear());
    }
}
