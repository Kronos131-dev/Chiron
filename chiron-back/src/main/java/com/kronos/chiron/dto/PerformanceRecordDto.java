package com.kronos.chiron.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceRecordDto {

    private String exerciseType;
    private Double poids;
    private Integer nombreReps;
}
