package com.kronos.chiron.utilisateur.service;

import com.kronos.chiron.utilisateur.dto.ProfileDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProfileService {

    ProfileDto getProfile(String username, String requestUsername);

    List<ProfileDto> searchProfiles(String query, String requestUsername);

    List<ProfileDto> getAllProfiles(String requestUsername);

    void updateVisibility(String username, boolean isPublic);

    String updateIcon(String username, MultipartFile file);

    void addCoach(String username, String coachUsername);

    void removeCoach(String username, String coachUsername);

    void deleteProfile(String username, String requestUsername);
}
