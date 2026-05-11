package com.kronos.chiron.service;

import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.mapper.SeanceMapper;
import com.kronos.chiron.repository.SeanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Service responsible for fetching user workout journal data, such as recent sessions.
 */
@Service
public class JournalService {

    private final SeanceRepository seanceRepository;
    private final SeanceMapper seanceMapper;

    /**
     * Constructs the JournalService with required dependencies.
     *
     * @param seanceRepository The repository for fetching Seance entities.
     * @param seanceMapper     The mapper component to convert entities to DTOs.
     */
    public JournalService(SeanceRepository seanceRepository, SeanceMapper seanceMapper) {
        this.seanceRepository = seanceRepository;
        this.seanceMapper = seanceMapper;
    }

    /**
     * Retrieves all workout sessions for a specific user that occurred during the current week.
     * Maps the resulting entities to Data Transfer Objects for client consumption.
     *
     * @param utilisateurId The ID of the user.
     * @return A list of mapped SeanceDto objects.
     */
    public List<SeanceDto> getSeancesForCurrentWeek(Long utilisateurId) {
        int currentWeek = getCurrentWeekNumber();
        List<Seance> seances = seanceRepository.findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(utilisateurId, currentWeek);

        return seances.stream()
                .map(seanceMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Calculates the current week number based on the local system calendar (France locale).
     *
     * @return The integer representing the current week of the year.
     */
    public int getCurrentWeekNumber() {
        WeekFields weekFields = WeekFields.of(Locale.FRANCE);
        return LocalDate.now().get(weekFields.weekOfWeekBasedYear());
    }
}
