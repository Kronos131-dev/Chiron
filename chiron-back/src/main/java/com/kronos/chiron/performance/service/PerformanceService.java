package com.kronos.chiron.performance.service;

import com.kronos.chiron.performance.dto.ExercisePerformanceDto;
import com.kronos.chiron.performance.dto.PerformanceRecordDto;
import com.kronos.chiron.performance.dto.PerformanceSummaryDto;

import java.util.*;

public interface PerformanceService {

    PerformanceSummaryDto getSummary(String username);

    PerformanceSummaryDto addRecord(String username, PerformanceRecordDto dto);

    PerformanceSummaryDto updateBodyweight(String username, Double poidsCorps);

    List<ExercisePerformanceDto> getHistory(String username, String exerciseTypeName);
}
