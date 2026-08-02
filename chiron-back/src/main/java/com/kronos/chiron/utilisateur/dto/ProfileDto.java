package com.kronos.chiron.utilisateur.dto;

import com.kronos.chiron.seance.dto.SeanceSummaryDto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto {

    private String username;

    private String icon;

    private String rank;

    @JsonProperty("isPublic")
    private Boolean isPublic;

    @JsonProperty("isMyCoach")
    private Boolean isMyCoach;

    @JsonProperty("amICoach")
    private Boolean amICoach;

    @JsonProperty("isAdmin")
    private Boolean isAdmin;

    private int totalSessions;

    private int averageSeriesPerMonth;

    private Double poidsCorps;

    private String performanceTier;

    private int performanceTierLevel;

    private List<SeanceSummaryDto> programmes;

    private List<SeanceSummaryDto> historiqueRecent;
}
