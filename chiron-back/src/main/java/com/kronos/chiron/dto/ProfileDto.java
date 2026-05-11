package com.kronos.chiron.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object representing a user profile's public and private information.
 * Aggregates statistics, visibility settings, and coaching relationships for frontend display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto {

    /**
     * The unique username of the profile.
     */
    private String username;

    /**
     * The profile's avatar icon filename.
     */
    private String icon;

    /**
     * The user's platform rank or status.
     */
    private String rank;

    /**
     * Indicates whether this profile is visible to all users.
     */
    @JsonProperty("isPublic")
    private boolean isPublic;

    /**
     * Indicates if the displayed profile serves as a coach for the currently authenticated viewer.
     */
    @JsonProperty("isMyCoach")
    private boolean isMyCoach;

    /**
     * Indicates if the currently authenticated viewer serves as a coach for this displayed profile.
     */
    @JsonProperty("amICoach")
    private boolean amICoach;

    /**
     * Indicates if the profile belongs to a system administrator.
     */
    @JsonProperty("isAdmin")
    private boolean isAdmin;

    /**
     * The total number of workout sessions logged by this user.
     */
    private int totalSessions;

    /**
     * The calculated average number of sets performed by this user per month.
     */
    private int averageSeriesPerMonth;

    /**
     * Summaries of workout templates or programs created by this user.
     */
    private List<SeanceSummaryDto> programmes;

    /**
     * Summaries of the user's recently completed workout sessions.
     */
    private List<SeanceSummaryDto> historiqueRecent;
}
