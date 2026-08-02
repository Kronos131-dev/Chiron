package com.kronos.chiron.seance.service;

import com.kronos.chiron.seance.dto.SeanceDto;

import java.util.List;

public interface JournalService {

    List<SeanceDto> getSeancesForCurrentWeek(Long utilisateurId);

    int getCurrentWeekNumber();
}
