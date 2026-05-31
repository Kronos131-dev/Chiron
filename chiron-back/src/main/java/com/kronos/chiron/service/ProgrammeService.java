package com.kronos.chiron.service;

import com.kronos.chiron.dto.ExerciceDto;
import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.dto.SerieDto;
import com.kronos.chiron.dto.DegressifDto;
import com.kronos.chiron.entity.CardioType;
import com.kronos.chiron.entity.Exercice;
import com.kronos.chiron.entity.ExerciceDefinition;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.entity.Serie;
import com.kronos.chiron.entity.Degressif;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.ExerciceDefinitionRepository;
import com.kronos.chiron.repository.SeanceRepository;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ExerciceDefinitionRepository exerciceDefinitionRepository;
    private final CardioCalorieService cardioCalorieService;

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
        return sauvegarderProgramme(username, seanceDto, null);
    }

    /**
     * Save a programme, optionally on behalf of another user (coach flow).
     *
     * @param username       The requester (authenticated user making the call).
     * @param seanceDto      The programme payload.
     * @param forUsername    If non-null and different from {@code username}, the programme is
     *                       saved for that athlete — requires the requester to be one of the
     *                       athlete's coaches (or an admin). Ignored on update (the owner of
     *                       the existing programme always takes precedence).
     */
    @Transactional
    public Seance sauvegarderProgramme(String username, SeanceDto seanceDto, String forUsername) {
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
            // Coach flow: requester is creating on behalf of `forUsername` (an athlete).
            if (forUsername != null && !forUsername.equals(username)) {
                Utilisateur athlete = utilisateurRepository.findByUsername(forUsername)
                        .orElseThrow(() -> new RuntimeException("Athlete not found: " + forUsername));
                if (!athlete.getCoaches().contains(requestUser) && requestUser.getRole() != Role.ADMIN) {
                    throw new RuntimeException("Access denied. You are not a coach of " + forUsername + ".");
                }
                targetUser = athlete;
            }
        }

        if (!isUpdate) {
            seance.setUtilisateur(targetUser);
            seance.setStartTime(seanceDto.startTime() != null ? seanceDto.startTime() : LocalDateTime.now());
            seance.setEndTime(seanceDto.endTime());
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
            int position = 0;
            for (ExerciceDto exoDto : seanceDto.exercices()) {
                Exercice exercice = new Exercice();
                exercice.setNom(exoDto.nom());
                exercice.setCommentaire(exoDto.commentaire());
                exercice.setDisplayOrder(position++);
                exercice.setBlockId(exoDto.blockId());
                exercice.setBlockType(exoDto.blockType());

                ExerciceDefinition definition = null;
                if (exoDto.exerciceDefinitionId() != null) {
                    definition = exerciceDefinitionRepository.findById(exoDto.exerciceDefinitionId()).orElse(null);
                    if (definition != null) exercice.setDefinition(definition);
                }
                CardioType cardioType = definition != null ? definition.getCardioType() : null;
                double poidsCorps = targetUser.getPoidsCorps() != null ? targetUser.getPoidsCorps() : 0.0;

                if (exoDto.series() != null) {
                    for (SerieDto serieDto : exoDto.series()) {
                        Serie serie = new Serie();
                        serie.setPoids(serieDto.poids() != null ? serieDto.poids() : 0.0);
                        serie.setNombreReps(serieDto.reps() != null ? serieDto.reps() : 0);
                        serie.setCommentaire(serieDto.commentaire());

                        // Paramètres cardio + calories (uniquement pour les exercices cardio)
                        serie.setDureeMin(serieDto.dureeMin());
                        serie.setDistanceM(serieDto.distanceM());
                        serie.setAllureKmh(serieDto.allureKmh());
                        serie.setPentePct(serieDto.pentePct());
                        if (cardioType != null) {
                            serie.setCalories(cardioCalorieService.estimate(
                                    cardioType, serieDto.dureeMin(), serieDto.allureKmh(),
                                    serieDto.pentePct(), serieDto.distanceM(), poidsCorps));
                        }

                        if (serieDto.degressifs() != null) {
                            for (DegressifDto degDto : serieDto.degressifs()) {
                                Degressif degressif = new Degressif();
                                degressif.setPoids(degDto.poids() != null ? degDto.poids() : 0.0);
                                degressif.setNombreReps(degDto.reps() != null ? degDto.reps() : 0);
                                serie.addDegressif(degressif);
                            }
                        }
                        
                        exercice.addSerie(serie);
                    }
                }
                seance.addExercice(exercice);
            }
        }

        Seance saved = seanceRepository.save(seance);

        if (seanceDto.exercices() != null) {
            Set<Long> usedDefinitionIds = seanceDto.exercices().stream()
                    .map(ExerciceDto::exerciceDefinitionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!usedDefinitionIds.isEmpty()) {
                exerciceDefinitionRepository.incrementUsageCount(usedDefinitionIds);
            }
        }

        return saved;
    }

    /**
     * Retrieves all workout programs (models) owned by the specified user.
     *
     * @param username The username of the program owner.
     * @return A list of Seance entities acting as models.
     */
    public List<Seance> getProgrammes(String username) {
        List<Seance> programmes = seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(username);
        logger.info("Found {} programmes for user {}", programmes.size(), username);
        return programmes;
    }

    /**
     * Persists a new manual display order for the user's programme templates.
     * The list of IDs defines the new order (first ID → top of the list).
     * Validates ownership (or coach / admin rights) for every programme in the list.
     *
     * @param username   The username of the requester performing the reorder.
     * @param orderedIds The programme IDs in the desired display order.
     */
    @Transactional
    public void reorderProgrammes(String username, List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return;

        Utilisateur requestUser = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Seance> programmes = seanceRepository.findAllById(orderedIds);
        if (programmes.size() != orderedIds.size()) {
            throw new RuntimeException("One or more programmes not found.");
        }

        for (Seance s : programmes) {
            Utilisateur owner = s.getUtilisateur();
            if (!owner.getUsername().equals(username)
                    && !owner.getCoaches().contains(requestUser)
                    && requestUser.getRole() != Role.ADMIN) {
                throw new RuntimeException("Access denied. You cannot reorder this programme.");
            }
        }

        java.util.Map<Long, Seance> byId = programmes.stream()
                .collect(Collectors.toMap(Seance::getId, s -> s));

        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).setDisplayOrder(i);
        }
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

        int copyPosition = 0;
        for (Exercice sourceExo : sourceSeance.getExercices()) {
            Exercice newExo = new Exercice();
            newExo.setNom(sourceExo.getNom());
            newExo.setCommentaire(sourceExo.getCommentaire());
            newExo.setDefinition(sourceExo.getDefinition());
            newExo.setDisplayOrder(copyPosition++);
            newExo.setBlockId(sourceExo.getBlockId());
            newExo.setBlockType(sourceExo.getBlockType());
            
            for (Serie sourceSerie : sourceExo.getSeries()) {
                Serie newSerie = new Serie();
                newSerie.setPoids(sourceSerie.getPoids());
                newSerie.setNombreReps(sourceSerie.getNombreReps());
                newSerie.setCommentaire(sourceSerie.getCommentaire());
                newSerie.setDureeMin(sourceSerie.getDureeMin());
                newSerie.setDistanceM(sourceSerie.getDistanceM());
                newSerie.setAllureKmh(sourceSerie.getAllureKmh());
                newSerie.setPentePct(sourceSerie.getPentePct());
                newSerie.setCalories(sourceSerie.getCalories());
                
                for (Degressif sourceDegressif : sourceSerie.getDegressifs()) {
                    Degressif newDegressif = new Degressif();
                    newDegressif.setPoids(sourceDegressif.getPoids());
                    newDegressif.setNombreReps(sourceDegressif.getNombreReps());
                    newSerie.addDegressif(newDegressif);
                }

                newExo.addSerie(newSerie);
            }
            newSeance.addExercice(newExo);
        }

        return seanceRepository.save(newSeance);
    }
}
