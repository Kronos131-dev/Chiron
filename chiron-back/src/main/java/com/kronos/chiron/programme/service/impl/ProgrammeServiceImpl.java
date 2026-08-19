package com.kronos.chiron.programme.service.impl;

import com.kronos.chiron.programme.service.ProgrammeService;

import java.time.Clock;

import static com.kronos.chiron.core.exceptions.ErrorFactory.forbidden;
import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.seance.service.CardioCalorieService;
import com.kronos.chiron.sante.service.ActiviteEnrichissementService;

import com.kronos.chiron.seance.dto.ExerciceDto;
import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.dto.SerieDto;
import com.kronos.chiron.seance.dto.DegressifDto;
import com.kronos.chiron.seance.model.CardioType;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.exercice.model.ExerciceDefinition;
import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.seance.model.Degressif;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.exercice.persistence.ExerciceDefinitionRepository;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
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

@Service
@RequiredArgsConstructor
public class ProgrammeServiceImpl implements ProgrammeService {

    private static final Logger logger = LoggerFactory.getLogger(ProgrammeService.class);

    private final SeanceRepository seanceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ExerciceDefinitionRepository exerciceDefinitionRepository;
    private final CardioCalorieService cardioCalorieService;
    private final ActiviteEnrichissementService activiteEnrichissementService;

    private final Clock clock;
    @Transactional
    @Override
    public Seance sauvegarderProgramme(String username, SeanceDto seanceDto) {
        return sauvegarderProgramme(username, seanceDto, null);
    }

    @Transactional
    @Override
    public Seance sauvegarderProgramme(String username, SeanceDto seanceDto, String forUsername) {
        Utilisateur requestUser = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found"));

        Seance seance = null;
        boolean isUpdate = false;
        Utilisateur targetUser = requestUser;

        if (seanceDto.id() != null) {
            Seance existingSeance = seanceRepository.findById(seanceDto.id())
                    .orElseThrow(() -> notFound("Program not found"));

            Utilisateur owner = existingSeance.getUtilisateur();
            if (!owner.getUsername().equals(username)) {
                if (!owner.getCoaches().contains(requestUser) && requestUser.getRole() != Role.ADMIN) {
                    throw forbidden("Access denied. You are not authorized to modify this program.");
                }
            }

            targetUser = owner;

            boolean requestedHistorique = seanceDto.historique() != null ? seanceDto.historique() : false;

            if (existingSeance.isHistorique() == requestedHistorique) {
                isUpdate = true;
                seance = existingSeance;
                seance.getExercices().clear();
            } else {
                isUpdate = false;
                seance = new Seance();
            }
        } else {
            seance = new Seance();
            if (forUsername != null && !forUsername.equals(username)) {
                Utilisateur athlete = utilisateurRepository.findByUsername(forUsername)
                        .orElseThrow(() -> notFound("Athlete not found: " + forUsername));
                if (!athlete.getCoaches().contains(requestUser) && requestUser.getRole() != Role.ADMIN) {
                    throw forbidden("Access denied. You are not a coach of " + forUsername + ".");
                }
                targetUser = athlete;
            }
        }

        if (!isUpdate) {
            seance.setUtilisateur(targetUser);
            seance.setStartTime(seanceDto.startTime() != null ? seanceDto.startTime() : LocalDateTime.now(clock));
            seance.setEndTime(seanceDto.endTime());
        }

        seance.setTitre(seanceDto.titre());
        seance.setNote(seanceDto.note());

        if (seanceDto.weekNumber() != null) {
            seance.setWeekNumber(seanceDto.weekNumber());
        }

        if (seanceDto.historique() != null) {
            seance.setHistorique(seanceDto.historique());
        } else {
            seance.setHistorique(false);
        }

        if (seanceDto.exercices() != null) {
            int position = 0;
            for (ExerciceDto exoDto : seanceDto.exercices()) {
                Exercice exercice = new Exercice();
                exercice.setNom(exoDto.nom());
                exercice.setCommentaire(exoDto.commentaire());
                exercice.setUnilateral(exoDto.unilateral());
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

        if (!isUpdate && Boolean.TRUE.equals(seanceDto.historique()) && saved.getEndTime() != null) {
            activiteEnrichissementService.planifierEnrichissement(saved);
        }

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

    @Override
    public List<Seance> getProgrammes(String username) {
        List<Seance> programmes = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueFalseOrderByDisplayOrderAscStartTimeDesc(username);
        logger.info("Found {} programmes for user {}", programmes.size(), username);
        return programmes;
    }

    @Transactional
    @Override
    public void reorderProgrammes(String username, List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return;

        Utilisateur requestUser = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found"));

        List<Seance> programmes = seanceRepository.findAllById(orderedIds);
        if (programmes.size() != orderedIds.size()) {
            throw notFound("One or more programmes not found.");
        }

        for (Seance s : programmes) {
            Utilisateur owner = s.getUtilisateur();
            if (!owner.getUsername().equals(username)
                    && !owner.getCoaches().contains(requestUser)
                    && requestUser.getRole() != Role.ADMIN) {
                throw forbidden("Access denied. You cannot reorder this programme.");
            }
        }

        java.util.Map<Long, Seance> byId = programmes.stream()
                .collect(Collectors.toMap(Seance::getId, s -> s));

        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).setDisplayOrder(i);
        }
    }

    @Override
    public Seance getProgrammeById(Long id, String username) {
        Seance seance = seanceRepository.findById(id)
                .orElseThrow(() -> notFound("Program not found"));

        Utilisateur requestUser = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found"));
        Utilisateur owner = seance.getUtilisateur();

        if (!owner.getUsername().equals(username)) {
            boolean isCoach = owner.getCoaches().contains(requestUser);
            boolean isPublic = owner.getIsPublic() != null && owner.getIsPublic();
            boolean isAdmin = requestUser.getRole() == Role.ADMIN;

            if (!isCoach && !isPublic && !isAdmin) {
                throw forbidden("Access denied. This profile is private.");
            }
        }

        return seance;
    }

    @Transactional
    @Override
    public void deleteProgramme(Long id, String username) {
        Seance seance = seanceRepository.findById(id)
                .orElseThrow(() -> notFound("Program not found"));

        Utilisateur requestUser = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("User not found"));
        Utilisateur owner = seance.getUtilisateur();

        if (!owner.getUsername().equals(username)) {
            if (!owner.getCoaches().contains(requestUser) && requestUser.getRole() != Role.ADMIN) {
                throw forbidden("Access denied. You cannot delete this program.");
            }
        }
        seanceRepository.delete(seance);
    }

    @Transactional
    @Override
    public Seance copyProgramme(Long programmeId, String targetUsername) {
        Utilisateur targetUser = utilisateurRepository.findByUsername(targetUsername)
                .orElseThrow(() -> notFound("Target user not found"));

        Seance sourceSeance = seanceRepository.findById(programmeId)
                .orElseThrow(() -> notFound("Source program not found"));

        if (!sourceSeance.getUtilisateur().getUsername().equals(targetUsername)) {
            boolean isPublic = sourceSeance.getUtilisateur().getIsPublic() != null
                    && sourceSeance.getUtilisateur().getIsPublic();
            boolean isAdmin = targetUser.getRole() == Role.ADMIN;

            if (!isPublic && !isAdmin) {
                throw forbidden("Cannot copy a program from a private profile.");
            }
        }

        Seance newSeance = new Seance();
        newSeance.setTitre(sourceSeance.getTitre() + " (Copie)");
        newSeance.setNote(sourceSeance.getNote());
        newSeance.setStartTime(LocalDateTime.now(clock));
        newSeance.setUtilisateur(targetUser);
        newSeance.setHistorique(false);
        newSeance.setWeekNumber(0);

        int copyPosition = 0;
        for (Exercice sourceExo : sourceSeance.getExercices()) {
            Exercice newExo = new Exercice();
            newExo.setNom(sourceExo.getNom());
            newExo.setCommentaire(sourceExo.getCommentaire());
            newExo.setUnilateral(sourceExo.isUnilateral());
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
