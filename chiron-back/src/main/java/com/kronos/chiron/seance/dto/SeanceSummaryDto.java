package com.kronos.chiron.seance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeanceSummaryDto {

    private Long id;

    private String titre;

    private LocalDateTime startTime;

    private int numberOfExercises;

    private int totalSeries;

    private boolean isModele;
}
