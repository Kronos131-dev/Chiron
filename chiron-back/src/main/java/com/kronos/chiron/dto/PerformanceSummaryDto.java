package com.kronos.chiron.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceSummaryDto {

    private String overallTier;
    private int overallTierLevel;
    private String overallTierCategorie;
    private Double poidsCorps;
    private List<ExercisePerformanceDto> exercises;
}
