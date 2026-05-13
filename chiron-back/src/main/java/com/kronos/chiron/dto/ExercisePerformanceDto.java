package com.kronos.chiron.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExercisePerformanceDto {

    private String exerciseType;
    private String nom;
    private Double poids;
    private Integer nombreReps;
    private Double rm1Estime;
    private Double ratioPerformance;
    private Double poidsCorporel;
    private String tier;
    private int tierLevel;
    private String tierCategorie;
    private LocalDateTime recordedAt;
}
