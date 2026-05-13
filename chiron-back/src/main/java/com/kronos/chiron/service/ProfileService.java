package com.kronos.chiron.service;

import com.kronos.chiron.dto.PerformanceSummaryDto;
import com.kronos.chiron.dto.ProfileDto;
import com.kronos.chiron.dto.SeanceSummaryDto;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.SeanceRepository;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for managing user profiles, visibility, ranks, and coaching relationships.
 * Also handles profile image uploads and aggregates session statistics.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UtilisateurRepository utilisateurRepository;
    private final SeanceRepository seanceRepository;
    private final PerformanceService performanceService;

    @Value("${chiron.uploads-dir:./uploads/images}")
    private String uploadsDir;

    /**
     * Retrieves the detailed profile of a specified user.
     * Enforces privacy checks and establishes coaching relationships relative to the requesting user.
     *
     * @param username        The username of the profile to retrieve.
     * @param requestUsername The username of the user making the request.
     * @return A ProfileDto containing user data, statistics, and workout summaries.
     */
    @Transactional
    public ProfileDto getProfile(String username, String requestUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found : " + username));

        Utilisateur requestUser = utilisateurRepository.findByUsername(requestUsername)
                .orElseThrow(() -> new RuntimeException("Requesting user not found : " + requestUsername));

        boolean amICoach = user.getCoaches().contains(requestUser);
        boolean isMyCoach = requestUser.getCoaches().contains(user);

        boolean isRequestUserAdmin = requestUser.getRole() == Role.ADMIN || "kronos".equalsIgnoreCase(requestUsername) || "chiron".equalsIgnoreCase(requestUsername);
        if (!username.equals(requestUsername) && (user.getIsPublic() == null || !user.getIsPublic()) && !isRequestUserAdmin && !amICoach) {
            throw new RuntimeException("Access denied. This profile is private.");
        }

        int averageSeriesPerMonth = calculateAverageSeriesPerMonth(user.getId());
        String rank = calculateRank(averageSeriesPerMonth);
        user.setRank(rank);
        utilisateurRepository.save(user);

        List<Seance> programmes = seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByStartTimeDesc(username);
        List<SeanceSummaryDto> programmeSummaries = programmes.stream()
                .map(this::toSeanceSummaryDto)
                .collect(Collectors.toList());

        List<Seance> historique = seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(username);
        List<SeanceSummaryDto> historiqueSummaries = historique.stream()
                .map(this::toSeanceSummaryDto)
                .collect(Collectors.toList());

        PerformanceSummaryDto performanceSummary = performanceService.getSummary(username);

        return ProfileDto.builder()
                .username(user.getUsername())
                .icon(user.getIcon())
                .rank(rank)
                .isPublic(user.getIsPublic() != null ? user.getIsPublic() : false)
                .isMyCoach(isMyCoach)
                .amICoach(amICoach)
                .isAdmin(user.getRole() == Role.ADMIN || "kronos".equalsIgnoreCase(user.getUsername()) || "chiron".equalsIgnoreCase(user.getUsername()))
                .totalSessions(historique.size())
                .averageSeriesPerMonth(averageSeriesPerMonth)
                .poidsCorps(user.getPoidsCorps())
                .performanceTier(performanceSummary.getOverallTier())
                .performanceTierLevel(performanceSummary.getOverallTierLevel())
                .programmes(programmeSummaries)
                .historiqueRecent(historiqueSummaries)
                .build();
    }

    /**
     * Searches for public user profiles matching a specific query string.
     * Admins and assigned coaches bypass visibility restrictions.
     *
     * @param query           The partial username to search for.
     * @param requestUsername The username of the user making the request.
     * @return A list of matching ProfileDto summaries.
     */
    @Transactional(readOnly = true)
    public List<ProfileDto> searchProfiles(String query, String requestUsername) {
        Utilisateur requestUser = utilisateurRepository.findByUsername(requestUsername)
                .orElseThrow(() -> new RuntimeException("Requesting user not found"));

        boolean isRequestUserAdmin = requestUser.getRole() == Role.ADMIN || "kronos".equalsIgnoreCase(requestUsername) || "chiron".equalsIgnoreCase(requestUsername);

        return utilisateurRepository.findByUsernameContainingIgnoreCase(query).stream()
                .filter(user -> {
                    if (isRequestUserAdmin) return true;
                    boolean isMyCoach = user.getCoaches().contains(requestUser);
                    return isMyCoach || (user.getIsPublic() != null && user.getIsPublic());
                })
                .map(user -> ProfileDto.builder()
                        .username(user.getUsername())
                        .icon(user.getIcon())
                        .rank(user.getRank())
                        .isAdmin(user.getRole() == Role.ADMIN || "kronos".equalsIgnoreCase(user.getUsername()) || "chiron".equalsIgnoreCase(user.getUsername()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all accessible user profiles in the system.
     * Admins and assigned coaches bypass visibility restrictions.
     *
     * @param requestUsername The username of the user making the request.
     * @return A list of ProfileDto summaries.
     */
    @Transactional(readOnly = true)
    public List<ProfileDto> getAllProfiles(String requestUsername) {
         Utilisateur requestUser = utilisateurRepository.findByUsername(requestUsername)
                 .orElseThrow(() -> new RuntimeException("Requesting user not found"));

         boolean isRequestUserAdmin = requestUser.getRole() == Role.ADMIN || "kronos".equalsIgnoreCase(requestUsername) || "chiron".equalsIgnoreCase(requestUsername);

         return utilisateurRepository.findAll().stream()
                .filter(user -> {
                    if (isRequestUserAdmin) return true;
                    boolean isMyCoach = user.getCoaches().contains(requestUser);
                    return isMyCoach || (user.getIsPublic() != null && user.getIsPublic());
                })
                .map(user -> {
                    PerformanceSummaryDto perf = performanceService.getSummary(user.getUsername());
                    return ProfileDto.builder()
                            .username(user.getUsername())
                            .icon(user.getIcon())
                            .rank(user.getRank())
                            .isAdmin(user.getRole() == Role.ADMIN || "kronos".equalsIgnoreCase(user.getUsername()) || "chiron".equalsIgnoreCase(user.getUsername()))
                            .performanceTier(perf.getOverallTier())
                            .performanceTierLevel(perf.getOverallTierLevel())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Updates the public visibility status of a user's profile.
     *
     * @param username The target username.
     * @param isPublic True to make the profile public, false to make it private.
     */
    @Transactional
    public void updateVisibility(String username, boolean isPublic) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsPublic(isPublic);
        utilisateurRepository.save(user);
    }

    /**
     * Uploads and updates the profile avatar image for a specific user.
     * Saves the image to the local filesystem.
     *
     * @param username The target username.
     * @param file     The multipart file containing the new image.
     * @return The generated filename of the newly saved image.
     */
    @Transactional
    public String updateIcon(String username, MultipartFile file) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        try {
            Path uploadPath = Paths.get(uploadsDir).toAbsolutePath().normalize();

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = username + "_" + UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            user.setIcon(fileName);
            utilisateurRepository.save(user);

            return fileName;

        } catch (IOException e) {
            throw new RuntimeException("Error while saving the image", e);
        }
    }

    /**
     * Designates another user as a coach for the requesting user.
     * Allows the coach to view the requester's private profile and edit their programs.
     *
     * @param username      The requesting user's username.
     * @param coachUsername The username of the user to be added as a coach.
     */
    @Transactional
    public void addCoach(String username, String coachUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Utilisateur coach = utilisateurRepository.findByUsername(coachUsername)
                .orElseThrow(() -> new RuntimeException("Coach not found"));

        user.addCoach(coach);
        utilisateurRepository.save(user);
    }

    /**
     * Removes coaching privileges from a previously designated coach.
     *
     * @param username      The requesting user's username.
     * @param coachUsername The username of the coach to be removed.
     */
    @Transactional
    public void removeCoach(String username, String coachUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Utilisateur coach = utilisateurRepository.findByUsername(coachUsername)
                .orElseThrow(() -> new RuntimeException("Coach not found"));

        user.removeCoach(coach);
        utilisateurRepository.save(user);
    }

    /**
     * Deletes a user profile and all associated data.
     * Validates that the requesting user is either the profile owner or an administrator.
     *
     * @param username        The username of the profile to delete.
     * @param requestUsername The username of the user making the request.
     */
    @Transactional
    public void deleteProfile(String username, String requestUsername) {
        Utilisateur userToDelete = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User to delete not found"));

        Utilisateur requestUser = utilisateurRepository.findByUsername(requestUsername)
                .orElseThrow(() -> new RuntimeException("Requesting user not found"));

        boolean isRequestUserAdmin = requestUser.getRole() == Role.ADMIN || "kronos".equalsIgnoreCase(requestUsername) || "chiron".equalsIgnoreCase(requestUsername);

        if (!username.equals(requestUsername) && !isRequestUserAdmin) {
            throw new RuntimeException("Access denied. You can only delete your own profile.");
        }

        // Handle bidirectional relationship cleanup before deletion
        for (Utilisateur coach : userToDelete.getCoaches()) {
            coach.getCoachedUsers().remove(userToDelete);
        }
        for (Utilisateur coachedUser : userToDelete.getCoachedUsers()) {
            coachedUser.getCoaches().remove(userToDelete);
        }
        userToDelete.getCoaches().clear();
        userToDelete.getCoachedUsers().clear();

        utilisateurRepository.delete(userToDelete);
    }

    /**
     * Calculates the total number of sets performed by the user over the last 30 days.
     *
     * @param userId The ID of the target user.
     * @return The sum of all series recorded in the past month.
     */
    private int calculateAverageSeriesPerMonth(Long userId) {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minus(1, ChronoUnit.MONTHS);
        Integer count = seanceRepository.countTotalSeriesForUserSince(userId, oneMonthAgo);
        return count != null ? count : 0;
    }

    /**
     * Determines the user's platform rank based on their monthly activity volume.
     *
     * @param averageSeries The total number of sets performed in the last month.
     * @return A string representing the calculated rank (e.g., "Olympien", "Spartiate").
     */
    private String calculateRank(int averageSeries) {
        if (averageSeries >= 200) return "Olympien";
        if (averageSeries >= 150) return "Héros";
        if (averageSeries >= 100) return "Spartiate";
        if (averageSeries >= 50) return "Athlète";
        return "Citoyen";
    }

    /**
     * Maps a full Seance entity to a lightweight SeanceSummaryDto.
     *
     * @param seance The Seance entity to map.
     * @return The mapped summary DTO.
     */
    private SeanceSummaryDto toSeanceSummaryDto(Seance seance) {
        int totalSeries = seance.getExercices().stream()
                .mapToInt(exo -> exo.getSeries().size())
                .sum();

        return SeanceSummaryDto.builder()
                .id(seance.getId())
                .titre(seance.getTitre())
                .startTime(seance.getStartTime())
                .numberOfExercises(seance.getExercices().size())
                .totalSeries(totalSeries)
                .isModele(seance.isModele())
                .build();
    }
}
