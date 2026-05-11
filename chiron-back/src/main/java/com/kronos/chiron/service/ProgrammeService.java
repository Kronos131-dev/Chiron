package com.kronos.chiron.service;

import com.kronos.chiron.dto.ExerciceDto;
import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.dto.SerieDto;
import com.kronos.chiron.entity.Exercice;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.entity.Serie;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.SeanceRepository;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for managing workout templates and programs.
 * Handles creation, modification, deletion, and copying of programs while enforcing
 * privacy and coach authorization rules.
 */
@Service
@RequiredArgsConstructor
public class ProgrammeService {

    private static final Logger logger = LoggerFactory.getLogger(ProgrammeService.class);

    private final SeanceRepository seanceRepository;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Saves a new workout program or updates an existing one based on the provided DTO.
     * Enforces ownership and coach modification rights.
     *
     * @param username  The username of the requester performing the save action.
     * @param seanceDto The data transfer object containing program details.
     * @return The persisted Seance entity.
     */
    @Transactional
    public Seance sauvegarderProgramme(String username, SeanceDto seanceDto) {
        Utilisateur requestUser = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Seance seance = null;
        boolean isUpdate = false;
        Utilisateur targetUser = requestUser;

        if (seanceDto.id() != null) {
            Seance existingSeance = seanceRepository.findById(seanceDto.id())
                    .orElseThrow(() -> new RuntimeException("Program not found"));

            Utilisateur owner = existingSeance.getUtilisateur();
            if (!owner.getUsername().equals(username)) {
                if (!owner.getCoaches().contains(requestUser) && requestUser.getRole() != Role.ADMIN) {
                    throw new RuntimeException("Access denied. You are not authorized to modify this program.");
                }
            }
            
            targetUser = owner;

            boolean requestedIsModele = seanceDto.isModele() != null ? seanceDto.isModele() : false;

            if (existingSeance.isModele() == requestedIsModele) {
                isUpdate = true;
                seance = existingSeance;
                seance.getExercices().clear();
            } else {
                isUpdate = false;
                seance = new Seance();
            }
        } else {
            seance = new Seance();
        }

        if (!isUpdate) {
            seance.setUtilisateur(targetUser);
            seance.setStartTime(seanceDto.startTime() != null ? seanceDto.startTime() : LocalDateTime.now());
        }

        seance.setTitre(seanceDto.titre());
        
        if (seanceDto.weekNumber() != null) {
            seance.setWeekNumber(seanceDto.weekNumber());
        }
        
        if (seanceDto.isModele() != null) {
            seance.setModele(seanceDto.isModele());
        } else {
            seance.setModele(false);
        }

        if (seanceDto.exercices() != null) {
            for (ExerciceDto exoDto : seanceDto.exercices()) {
                Exercice exercice = new Exercice();
                exercice.setNom(exoDto.nom());
                exercice.setCommentaire(exoDto.commentaire());

                if (exoDto.series() != null) {
                    for (SerieDto serieDto : exoDto.series()) {
                        Serie serie = new Serie();
                        serie.setPoids(serieDto.poids() != null ? serieDto.poids() : 0.0);
                        serie.setNombreReps(serieDto.reps() != null ? serieDto.reps() : 0);
                        serie.setCommentaire(serieDto.commentaire());
                        exercice.addSerie(serie);
                    }
                }
                seance.addExercice(exercice);
            }
        }

        return seanceRepository.save(seance);
    }

    /**
     * Retrieves all workout programs (models) owned by the specified user.
     *
     * @param username The username of the program owner.
     * @return A list of Seance entities acting as models.
     */
    public List<Seance> getProgrammes(String username) {
        List<Seance> programmes = seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByStartTimeDesc(username);
        logger.info("Found {} programmes for user {}", programmes.size(), username);
        return programmes;
    }

    /**
     * Retrieves a specific workout program by its ID.
     * Validates access rights to ensure the requesting user is allowed to view it.
     *
     * @param id       The ID of the program.
     * @param username The username of the user requesting the program.
     * @return The requested Seance entity.
     */
    public Seance getProgrammeById(Long id, String username) {
        Seance seance = seanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program not found"));
        
        Utilisateur requestUser = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Utilisateur owner = seance.getUtilisateur();

        if (!owner.getUsername().equals(username)) {
             boolean isCoach = owner.getCoaches().contains(requestUser);
             boolean isPublic = owner.getIsPublic() != null && owner.getIsPublic();
             boolean isAdmin = requestUser.getRole() == Role.ADMIN;

             if (!isCoach && !isPublic && !isAdmin) {
                 throw new RuntimeException("Access denied. This profile is private.");
             }
        }
        
        return seance;
    }

    /**
     * Deletes a specific workout program.
     * Validates that the requesting user has the appropriate ownership or coaching rights.
     *
     * @param id       The ID of the program to delete.
     * @param username The username of the user requesting the deletion.
     */
    @Transactional
    public void deleteProgramme(Long id, String username) {
        Seance seance = seanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program not found"));

        Utilisateur requestUser = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Utilisateur owner = seance.getUtilisateur();

        if (!owner.getUsername().equals(username)) {
             if (!owner.getCoaches().contains(requestUser) && requestUser.getRole() != Role.ADMIN) {
                 throw new RuntimeException("Access denied. You cannot delete this program.");
             }
        }
        seanceRepository.delete(seance);
    }

    /**
     * Creates a deep copy of an existing program and assigns it to the requesting user.
     * Validates that the source program is publicly accessible or explicitly shared.
     *
     * @param programmeId    The ID of the source program to copy.
     * @param targetUsername The username of the user who will own the new copy.
     * @return The newly persisted copied Seance entity.
     */
    @Transactional
    public Seance copyProgramme(Long programmeId, String targetUsername) {
        Utilisateur targetUser = utilisateurRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        Seance sourceSeance = seanceRepository.findById(programmeId)
                .orElseThrow(() -> new RuntimeException("Source program not found"));

        if (!sourceSeance.getUtilisateur().getUsername().equals(targetUsername)) {
            boolean isPublic = sourceSeance.getUtilisateur().getIsPublic() != null && sourceSeance.getUtilisateur().getIsPublic();
            boolean isAdmin = targetUser.getRole() == Role.ADMIN;
            
            if (!isPublic && !isAdmin) {
                 throw new RuntimeException("Cannot copy a program from a private profile.");
            }
        }

        Seance newSeance = new Seance();
        newSeance.setTitre(sourceSeance.getTitre() + " (Copie)");
        newSeance.setStartTime(LocalDateTime.now());
        newSeance.setUtilisateur(targetUser);
        newSeance.setModele(false);
        newSeance.setWeekNumber(0);

        for (Exercice sourceExo : sourceSeance.getExercices()) {
            Exercice newExo = new Exercice();
            newExo.setNom(sourceExo.getNom());
            newExo.setCommentaire(sourceExo.getCommentaire());
            
            for (Serie sourceSerie : sourceExo.getSeries()) {
                Serie newSerie = new Serie();
                newSerie.setPoids(sourceSerie.getPoids());
                newSerie.setNombreReps(sourceSerie.getNombreReps());
                newSerie.setCommentaire(sourceSerie.getCommentaire());
                newExo.addSerie(newSerie);
            }
            newSeance.addExercice(newExo);
        }

        return seanceRepository.save(newSeance);
    }
}
