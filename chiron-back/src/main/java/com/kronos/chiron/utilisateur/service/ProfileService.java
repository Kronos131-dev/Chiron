package com.kronos.chiron.utilisateur.service;

import java.time.Clock;

import static com.kronos.chiron.core.exceptions.ErrorFactory.badRequest;
import static com.kronos.chiron.core.exceptions.ErrorFactory.forbidden;
import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;
import com.kronos.chiron.core.exceptions.ChironTechnicalException;

import com.kronos.chiron.performance.service.PerformanceService;

import com.kronos.chiron.performance.dto.PerformanceSummaryDto;
import com.kronos.chiron.utilisateur.dto.ProfileDto;
import com.kronos.chiron.seance.dto.SeanceSummaryDto;
import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
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

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UtilisateurRepository utilisateurRepository;
    private final SeanceRepository seanceRepository;
    private final PerformanceService performanceService;

    private final Clock clock;
    @Value("${chiron.uploads-dir:./uploads/images}")
    private String uploadsDir;

    @Transactional
    public ProfileDto getProfile(String username, String requestUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found : " + username));

        Utilisateur requestUser = utilisateurRepository.findByUsername(requestUsername)
                .orElseThrow(() -> notFound("Requesting user not found : " + requestUsername));

        boolean amICoach = user.getCoaches().contains(requestUser);
        boolean isMyCoach = requestUser.getCoaches().contains(user);

        boolean isRequestUserAdmin = requestUser.getRole() == Role.ADMIN || "kronos".equalsIgnoreCase(requestUsername) || "chiron".equalsIgnoreCase(requestUsername);
        if (!username.equals(requestUsername) && (user.getIsPublic() == null || !user.getIsPublic()) && !isRequestUserAdmin && !amICoach) {
            throw forbidden("Access denied. This profile is private.");
        }

        int averageSeriesPerMonth = calculateAverageSeriesPerMonth(user.getId());
        String rank = calculateRank(averageSeriesPerMonth);
        user.setRank(rank);
        utilisateurRepository.save(user);

        List<Seance> programmes = seanceRepository.findByUtilisateurUsernameAndHistoriqueFalseOrderByDisplayOrderAscStartTimeDesc(username);
        List<SeanceSummaryDto> programmeSummaries = programmes.stream()
                .map(this::toSeanceSummaryDto)
                .collect(Collectors.toList());

        List<Seance> historique = seanceRepository.findByUtilisateurUsernameAndHistoriqueTrueOrderByStartTimeDesc(username);
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

    @Transactional(readOnly = true)
    public List<ProfileDto> searchProfiles(String query, String requestUsername) {
        Utilisateur requestUser = utilisateurRepository.findByUsername(requestUsername)
                .orElseThrow(() -> notFound("Requesting user not found"));

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

    @Transactional(readOnly = true)
    public List<ProfileDto> getAllProfiles(String requestUsername) {
         Utilisateur requestUser = utilisateurRepository.findByUsername(requestUsername)
                 .orElseThrow(() -> notFound("Requesting user not found"));

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

    @Transactional
    public void updateVisibility(String username, boolean isPublic) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found"));
        user.setIsPublic(isPublic);
        utilisateurRepository.save(user);
    }

    @Transactional
    public String updateIcon(String username, MultipartFile file) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found"));

        if (file.isEmpty()) {
            throw badRequest("File is empty");
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
            throw new ChironTechnicalException("Error while saving the image", e);
        }
    }

    @Transactional
    public void addCoach(String username, String coachUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found"));
        Utilisateur coach = utilisateurRepository.findByUsername(coachUsername)
                .orElseThrow(() -> notFound("Coach not found"));

        user.addCoach(coach);
        utilisateurRepository.save(user);
    }

    @Transactional
    public void removeCoach(String username, String coachUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found"));
        Utilisateur coach = utilisateurRepository.findByUsername(coachUsername)
                .orElseThrow(() -> notFound("Coach not found"));

        user.removeCoach(coach);
        utilisateurRepository.save(user);
    }

    @Transactional
    public void deleteProfile(String username, String requestUsername) {
        Utilisateur userToDelete = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User to delete not found"));

        Utilisateur requestUser = utilisateurRepository.findByUsername(requestUsername)
                .orElseThrow(() -> notFound("Requesting user not found"));

        boolean isRequestUserAdmin = requestUser.getRole() == Role.ADMIN || "kronos".equalsIgnoreCase(requestUsername) || "chiron".equalsIgnoreCase(requestUsername);

        if (!username.equals(requestUsername) && !isRequestUserAdmin) {
            throw forbidden("Access denied. You can only delete your own profile.");
        }

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

    private int calculateAverageSeriesPerMonth(Long userId) {
        LocalDateTime oneMonthAgo = LocalDateTime.now(clock).minus(1, ChronoUnit.MONTHS);
        Integer count = seanceRepository.countTotalSeriesForUserSince(userId, oneMonthAgo);
        return count != null ? count : 0;
    }

    private String calculateRank(int averageSeries) {
        if (averageSeries >= 200) return "Olympien";
        if (averageSeries >= 150) return "Héros";
        if (averageSeries >= 100) return "Spartiate";
        if (averageSeries >= 50) return "Athlète";
        return "Citoyen";
    }

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
                .historique(seance.isHistorique())
                .build();
    }
}
