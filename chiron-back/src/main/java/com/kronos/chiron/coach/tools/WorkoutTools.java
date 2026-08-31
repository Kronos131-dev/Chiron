package com.kronos.chiron.coach.tools;

import java.time.Clock;

import com.kronos.chiron.coach.context.VoiceSessionContext;
import com.kronos.chiron.exercice.dto.ExerciceDefinitionDto;
import com.kronos.chiron.seance.dto.ExerciceDto;
import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.dto.SerieDto;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.exercice.model.ExerciceDefinition;
import com.kronos.chiron.exercice.model.MuscleGroup;
import com.kronos.chiron.exercice.model.NiveauDifficulte;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.exercice.model.TypeEquipement;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.model.Role;
import java.time.Period;
import com.kronos.chiron.exercice.persistence.ExerciceDefinitionRepository;
import com.kronos.chiron.seance.persistence.ExerciceRepository;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.performance.dto.ExercisePerformanceDto;
import com.kronos.chiron.performance.dto.PerformanceSummaryDto;
import com.kronos.chiron.exercice.service.ExerciceDefinitionService;
import com.kronos.chiron.performance.service.PerformanceService;
import com.kronos.chiron.programme.service.ProgrammeService;
import com.kronos.chiron.sante.service.ActiviteEnrichissementService;
import com.kronos.chiron.fitbit.service.FitbitPushService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WorkoutTools {

    private final SeanceRepository seanceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ExerciceRepository exerciceRepository;
    private final ExerciceDefinitionService exerciceDefinitionService;
    private final ExerciceDefinitionRepository exerciceDefinitionRepository;
    private final ProgrammeService programmeService;
    private final PerformanceService performanceService;
    private final ToolUserResolver toolUserResolver;
    private final ActiviteEnrichissementService activiteEnrichissementService;
    private final FitbitPushService fitbitPushService;
    private final VoiceSessionContext voiceSessionContext;

    private final Clock clock;
    @Tool("Retourne la date et l'heure actuelles et le jour de la semaine.")
    public String getCurrentDate() {
        LocalDateTime now = LocalDateTime.now(clock);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy à HH:mm", Locale.FRANCE);
        return "Nous sommes le " + now.format(formatter);
    }

    @Tool("Récupère la liste des modèles de programmes d'entraînement (presets) de l'utilisateur ou d'un autre utilisateur spécifié.")
    public String getUserProgrammes(@ToolMemoryId String memoryId, String targetUsername) {
        Utilisateur requestUser = toolUserResolver.load(memoryId);

        String searchUsername = (targetUsername != null && !targetUsername.isBlank())
                ? targetUsername
                : requestUser.getUsername();

        Utilisateur targetUser = utilisateurRepository.findByUsername(searchUsername)
                .orElse(null);

        if (targetUser == null) {
            return "L'utilisateur '" + searchUsername + "' est introuvable.";
        }

        if (!requestUser.getUsername().equals(searchUsername) && requestUser.getRole() != Role.ADMIN) {
            if (targetUser.getIsPublic() == null || !targetUser.getIsPublic()) {
                return "Le profil de l'utilisateur '" + searchUsername
                        + "' est privé. Vous n'avez pas l'autorisation de voir ses programmes.";
            }
        }

        List<Seance> programmes = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueFalseOrderByDisplayOrderAscStartTimeDesc(
                        targetUser.getUsername());

        if (programmes.isEmpty()) {
            return "L'utilisateur " + searchUsername + " n'a aucun modèle de programme d'entraînement enregistré.";
        }

        StringBuilder res = new StringBuilder(
                "L'utilisateur " + searchUsername + " a " + programmes.size() + " programmes enregistrés :\n");
        for (Seance s : programmes) {
            res.append("- Programme '").append(s.getTitre()).append("' (");
            if (s.getExercices() != null && !s.getExercices().isEmpty()) {
                String exos = s.getExercices().stream().map(this::exoLabel).collect(Collectors.joining(", "));
                res.append("Exercices : ").append(exos);
            } else {
                res.append("Aucun exercice");
            }
            res.append(")\n");
        }
        return res.toString();
    }

    @Tool("Récupère l'historique complet ou récent des séances réellement effectuées par l'utilisateur ou un autre utilisateur spécifié.")
    public String getUserHistory(@ToolMemoryId String memoryId, String targetUsername) {
        Utilisateur requestUser = toolUserResolver.load(memoryId);

        String searchUsername = (targetUsername != null && !targetUsername.isBlank())
                ? targetUsername
                : requestUser.getUsername();

        Utilisateur targetUser = utilisateurRepository.findByUsername(searchUsername)
                .orElse(null);

        if (targetUser == null) {
            return "L'utilisateur '" + searchUsername + "' est introuvable.";
        }

        if (!requestUser.getUsername().equals(searchUsername) && requestUser.getRole() != Role.ADMIN) {
            if (targetUser.getIsPublic() == null || !targetUser.getIsPublic()) {
                return "Le profil de l'utilisateur '" + searchUsername
                        + "' est privé. Vous n'avez pas l'autorisation de voir son historique.";
            }
        }

        List<Seance> historique = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueTrueOrderByStartTimeDesc(targetUser.getUsername());

        if (historique.isEmpty()) {
            return "L'utilisateur " + searchUsername + " n'a encore enregistré aucune séance dans son historique.";
        }

        StringBuilder res = new StringBuilder(
                "Voici l'historique des séances effectuées par l'utilisateur " + searchUsername + " :\n");
        for (Seance s : historique) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String dateStr = s.getStartTime() != null ? s.getStartTime().format(formatter) : "Date inconnue";

            res.append("- Le ").append(dateStr).append(" : Séance '")
                    .append(s.getTitre() != null ? s.getTitre() : "Sans nom").append("' (");

            if (s.getExercices() != null && !s.getExercices().isEmpty()) {
                String exos = s.getExercices().stream().map(this::exoLabel).collect(Collectors.joining(", "));
                res.append("Exercices : ").append(exos);
            } else {
                res.append("Aucun exercice");
            }
            res.append(")\n");
        }
        return res.toString();
    }

    @Tool("Démarre une nouvelle séance d'entraînement dans l'historique de l'utilisateur.")
    public String startSession(@ToolMemoryId String memoryId, String titre) {
        if (voiceSessionContext.getPinnedSeanceId() != null) {
            return "La séance est déjà en cours, inutile de la redémarrer.";
        }

        Utilisateur user = toolUserResolver.load(memoryId);

        seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(user.getId())
                .ifPresent(s -> {
                    s.setEndTime(LocalDateTime.now(clock));
                    seanceRepository.save(s);
                });

        int currentWeek = LocalDate.now(clock).get(WeekFields.of(Locale.FRANCE).weekOfWeekBasedYear());

        Seance seance = Seance.builder()
                .titre(titre)
                .startTime(LocalDateTime.now(clock))
                .weekNumber(currentWeek)
                .historique(true)
                .utilisateur(user)
                .build();

        if (seance.getExercices() == null) seance.setExercices(new ArrayList<>());

        seanceRepository.save(seance);
        return "Séance '" + titre + "' démarrée en base de données. Tu peux maintenant utiliser [startExercise].";
    }

    @Tool("Crée un NOUVEAU modèle de programme d'entraînement (preset) VIDE.")
    public String createProgramModel(@ToolMemoryId String memoryId, String titre) {
        Utilisateur user = toolUserResolver.load(memoryId);

        Seance seance = Seance.builder()
                .titre(titre)
                .startTime(LocalDateTime.now(clock))
                .weekNumber(0)
                .historique(false)
                .utilisateur(user)
                .build();

        if (seance.getExercices() == null) seance.setExercices(new ArrayList<>());

        seanceRepository.save(seance);
        return "Modèle de programme '" + titre
                + "' créé avec succès. Dis à l'utilisateur qu'il peut le modifier depuis l'interface 'Programme'.";
    }

    @Tool("Démarre un nouvel exercice dans la séance en cours.")
    public String startExercise(@ToolMemoryId String memoryId, String nomExercice) {
        Seance activeSeance = getActiveSeance(memoryId);

        if (activeSeance == null) {
            return "ERREUR SYSTEME : Impossible d'ajouter l'exercice. L'utilisateur doit d'abord démarrer une séance. Appelle l'outil [startSession] en premier !";
        }

        if (activeSeance.getExercices() == null) activeSeance.setExercices(new ArrayList<>());

        int nextPosition = activeSeance.getExercices().stream()
                .mapToInt(Exercice::getDisplayOrder)
                .max()
                .orElse(-1) + 1;

        // WHY: relier l'exercice à la bibliothèque standardisée conditionne les analyses : sans
        // ce lien, [getFullExerciseHistory], [getPersonalRecord]… le considèrent comme non [std]
        // et refusent toute analyse de progression.
        ExerciceDefinition definition = exerciceDefinitionService.search(nomExercice, null, null, null)
                .stream().findFirst()
                .flatMap(dto -> exerciceDefinitionRepository.findById(dto.id()))
                .orElse(null);

        Exercice exercice = Exercice.builder()
                .nom(nomExercice)
                .definition(definition)
                .startTime(LocalDateTime.now(clock))
                .displayOrder(nextPosition)
                .build();

        if (exercice.getSeries() == null) exercice.setSeries(new ArrayList<>());

        activeSeance.addExercice(exercice);
        seanceRepository.save(activeSeance);
        return "Exercice '" + nomExercice
                + "' ajouté à la séance. Tu peux maintenant utiliser [addSet] pour cet exercice.";
    }

    @Tool("Enregistre une série (poids, répétitions, commentaire) pour l'exercice en cours.")
    public String addSet(@ToolMemoryId String memoryId, double poids, int reps, String commentaire,
            String nomExercice) {
        Seance activeSeance = getActiveSeance(memoryId);

        if (activeSeance == null) {
            return "ERREUR SYSTEME : Aucune séance n'existe. Tu dois appeler [startSession] puis [startExercise] d'abord !";
        }

        if (activeSeance.getExercices() == null || activeSeance.getExercices().isEmpty()) {
            return "ERREUR SYSTEME : Aucun exercice n'est en cours. Tu dois appeler l'outil [startExercise] d'abord !";
        }

        Exercice activeExercice = resolveExercice(activeSeance, nomExercice);

        Serie serie = Serie.builder()
                .poids(poids)
                .nombreReps(reps)
                .commentaire(commentaire)
                .build();

        if (activeExercice.getSeries() == null) activeExercice.setSeries(new ArrayList<>());

        activeExercice.addSerie(serie);
        seanceRepository.save(activeSeance);
        return "Succès : Série enregistrée (" + reps + "x" + poids + "kg).";
    }

    @Tool("Modifie une série déjà enregistrée de l'exercice en cours : change le poids et/ou le nombre "
            + "de répétitions. Cible la dernière série par défaut, ou une série précise via son numéro (1 = première).")
    public String modifySet(@ToolMemoryId String memoryId, Integer numeroSerie, Double nouveauPoids,
            Integer nouvellesReps, String nomExercice) {
        Seance activeSeance = getActiveSeance(memoryId);

        if (activeSeance == null) {
            return "ERREUR SYSTEME : Aucune séance n'existe. Tu dois appeler [startSession] puis [startExercise] d'abord !";
        }

        if (activeSeance.getExercices() == null || activeSeance.getExercices().isEmpty()) {
            return "ERREUR SYSTEME : Aucun exercice n'est en cours. Tu dois appeler l'outil [startExercise] d'abord !";
        }

        Exercice activeExercice = resolveExercice(activeSeance, nomExercice);

        if (activeExercice.getSeries() == null || activeExercice.getSeries().isEmpty()) {
            return "ERREUR SYSTEME : Aucune série enregistrée. Tu dois appeler [addSet] d'abord !";
        }

        int targetIndex = (numeroSerie != null && numeroSerie > 0)
                ? numeroSerie - 1
                : activeExercice.getSeries().size() - 1;
        if (targetIndex < 0 || targetIndex >= activeExercice.getSeries().size()) {
            return "Erreur : Le numéro de série " + numeroSerie + " est invalide.";
        }

        Serie targetSerie = activeExercice.getSeries().get(targetIndex);
        if (nouveauPoids != null) targetSerie.setPoids(nouveauPoids);
        if (nouvellesReps != null) targetSerie.setNombreReps(nouvellesReps);

        seanceRepository.save(activeSeance);
        return "Succès : Série " + (targetIndex + 1) + " modifiée (" + nouvellesReps + "x" + nouveauPoids + "kg).";
    }

    @Tool("Termine la séance d'entraînement en cours.")
    public String endSession(@ToolMemoryId String memoryId) {
        Seance activeSeance = getActiveSeance(memoryId);

        if (activeSeance == null) {
            return "L'utilisateur a demandé à terminer, mais il n'y a aucune séance en cours.";
        }

        activeSeance.setEndTime(LocalDateTime.now(clock));

        if (activeSeance.getExercices() != null && !activeSeance.getExercices().isEmpty()) {
            Exercice lastExo = activeSeance.getExercices().get(activeSeance.getExercices().size() - 1);
            if (lastExo.getEndTime() == null) {
                lastExo.setEndTime(LocalDateTime.now(clock));
            }
        }

        seanceRepository.save(activeSeance);
        activiteEnrichissementService.planifierEnrichissement(activeSeance);
        fitbitPushService.pousserSeance(activeSeance.getId());
        return "Séance terminée et sauvegardée avec succès dans l'historique.";
    }

    @Tool("Récupère les performances de la DERNIÈRE FOIS que l'utilisateur a fait un exercice spécifique.")
    public String getLastExercisePerformance(@ToolMemoryId String memoryId, String nomExercice) {
        Optional<Exercice> lastExoOpt = exerciceRepository
                .findFirstHistoricExercise(toolUserResolver.loadId(memoryId), nomExercice);

        if (lastExoOpt.isEmpty()) {
            return "Information pour l'IA : L'utilisateur n'a jamais fait l'exercice '" + nomExercice
                    + "' dans son historique.";
        }

        Exercice exo = lastExoOpt.get();
        if (exo.getDefinition() == null) {
            return "L'exercice '" + nomExercice
                    + "' n'est pas dans la base standardisée [std]. Analyse de progression indisponible.";
        }

        StringBuilder reponse = new StringBuilder();

        if (exo.getStartTime() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy à HH:mm", Locale.FRANCE);
            reponse.append("Dernière performance trouvée le ").append(exo.getStartTime().format(formatter))
                    .append(".\n");
        } else {
            reponse.append("Dernière performance trouvée (date inconnue).\n");
        }

        if (exo.getSeries() == null || exo.getSeries().isEmpty()) {
            return reponse.append("Mais aucune série n'a été enregistrée.").toString();
        }

        reponse.append("Voici les séries effectuées :\n");
        for (int i = 0; i < exo.getSeries().size(); i++) {
            Serie s = exo.getSeries().get(i);
            reponse.append("- Série ").append(i + 1).append(" : ").append(s.getNombreReps())
                    .append(" reps à ").append(s.getPoids()).append(" kg.\n");
        }
        return reponse.toString();
    }

    @Tool("Recherche les profils utilisateurs existants (utile pour un admin qui cherche quelqu'un).")
    public String searchAllProfiles(@ToolMemoryId String memoryId, String query) {
        Utilisateur requestUser = toolUserResolver.load(memoryId);

        if (requestUser.getRole() != Role.ADMIN) {
            return "Accès refusé. Seul un administrateur peut effectuer cette recherche globale.";
        }

        List<Utilisateur> users = utilisateurRepository.findByUsernameContainingIgnoreCase(query);
        if (users.isEmpty()) {
            return "Aucun profil trouvé pour la recherche : " + query;
        }

        StringBuilder res = new StringBuilder("Profils trouvés :\n");
        for (Utilisateur u : users) {
            res.append("- ").append(u.getUsername()).append(" (Rang: ").append(u.getRank())
                    .append(", Public: ").append(u.getIsPublic() != null ? u.getIsPublic() : false).append(")\n");
        }
        return res.toString();
    }

    @Tool("Récupère un résumé détaillé des séances de sport effectuées à une date précise (format attendu YYYY-MM-DD).")
    public String getWorkoutSummaryByDate(@ToolMemoryId String memoryId, String dateStr) {
        Utilisateur user = toolUserResolver.load(memoryId);

        List<Seance> historique = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueTrueOrderByStartTimeDesc(user.getUsername());

        StringBuilder res = new StringBuilder();
        boolean found = false;

        for (Seance s : historique) {
            if (s.getStartTime() != null && s.getStartTime().toLocalDate().toString().equals(dateStr)) {
                found = true;
                res.append("- Séance '").append(s.getTitre() != null ? s.getTitre() : "Sans nom").append("' :\n");
                if (s.getExercices() != null) {
                    for (Exercice e : s.getExercices()) {
                        res.append("  * ").append(exoLabel(e)).append(" : ");
                        if (e.getSeries() != null && !e.getSeries().isEmpty()) {
                            String seriesDetails = e.getSeries().stream()
                                    .map(serie -> serie.getNombreReps() + " reps @ " + serie.getPoids() + "kg")
                                    .collect(Collectors.joining(" | "));
                            res.append(seriesDetails);
                        } else {
                            res.append("Aucune série");
                        }
                        res.append("\n");
                    }
                }
                res.append("\n");
            }
        }
        return found ? res.toString() : "Aucune séance trouvée à la date du " + dateStr + ".";
    }

    @Tool("Récupère le contenu détaillé d'un programme (exercices, séries, poids, répétitions) à partir de son nom.")
    public String getProgrammeDetails(@ToolMemoryId String memoryId, String programmeName) {
        Utilisateur user = toolUserResolver.load(memoryId);

        List<Seance> programmes = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueFalseOrderByDisplayOrderAscStartTimeDesc(user.getUsername());

        List<Seance> matching = programmes.stream()
                .filter(s -> s.getTitre() != null && s.getTitre().toLowerCase().contains(programmeName.toLowerCase()))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            return "Aucun programme trouvé avec le nom '" + programmeName
                    + "'. Utilise [getUserProgrammes] pour voir les programmes disponibles.";
        }

        StringBuilder res = new StringBuilder();
        for (Seance s : matching) {
            res.append("Programme : '").append(s.getTitre()).append("'\n");
            if (s.getExercices() == null || s.getExercices().isEmpty()) {
                res.append("  Aucun exercice enregistré dans ce programme.\n");
            } else {
                for (Exercice e : s.getExercices()) {
                    res.append("  - ").append(exoLabel(e)).append(" : ");
                    if (e.getSeries() != null && !e.getSeries().isEmpty()) {
                        String seriesDetails = e.getSeries().stream()
                                .map(serie -> serie.getNombreReps() + " reps @ " + serie.getPoids() + "kg")
                                .collect(Collectors.joining(" | "));
                        res.append(seriesDetails);
                    } else {
                        res.append("Aucune série définie");
                    }
                    res.append("\n");
                }
            }
            res.append("\n");
        }
        return res.toString();
    }

    @Tool("Récupère les détails complets d'une ou plusieurs séances historiques à partir de leur titre (exercices, séries, poids, commentaires).")
    public String getSessionDetails(@ToolMemoryId String memoryId, String sessionTitle) {
        Utilisateur user = toolUserResolver.load(memoryId);

        List<Seance> historique = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueTrueOrderByStartTimeDesc(user.getUsername());

        List<Seance> matching = historique.stream()
                .filter(s -> s.getTitre() != null && s.getTitre().toLowerCase().contains(sessionTitle.toLowerCase()))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            return "Aucune séance trouvée avec le titre '" + sessionTitle
                    + "'. Utilise [getUserHistory] pour voir l'historique complet.";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy à HH:mm", Locale.FRANCE);
        StringBuilder res = new StringBuilder();
        for (Seance s : matching) {
            String dateStr = s.getStartTime() != null ? s.getStartTime().format(formatter) : "Date inconnue";
            res.append("Séance '").append(s.getTitre()).append("' — ").append(dateStr).append("\n");
            if (s.getExercices() == null || s.getExercices().isEmpty()) {
                res.append("  Aucun exercice enregistré.\n");
            } else {
                for (Exercice e : s.getExercices()) {
                    res.append("  - ").append(exoLabel(e)).append(" : ");
                    if (e.getSeries() != null && !e.getSeries().isEmpty()) {
                        String seriesDetails = e.getSeries().stream()
                                .map(serie -> {
                                    String detail = serie.getNombreReps() + " reps @ " + serie.getPoids() + "kg";
                                    if (serie.getCommentaire() != null && !serie.getCommentaire().isBlank()) {
                                        detail += " (" + serie.getCommentaire() + ")";
                                    }
                                    return detail;
                                })
                                .collect(Collectors.joining(" | "));
                        res.append(seriesDetails);
                    } else {
                        res.append("Aucune série");
                    }
                    res.append("\n");
                }
            }
            res.append("\n");
        }
        return res.toString();
    }

    @Tool("Récupère l'historique COMPLET de toutes les fois où l'utilisateur a effectué un exercice donné, avec les détails de chaque série.")
    public String getFullExerciseHistory(@ToolMemoryId String memoryId, String nomExercice) {
        List<Exercice> exercises = exerciceRepository.findAllHistoricExercises(toolUserResolver.loadId(memoryId),
                nomExercice);

        if (exercises.isEmpty()) {
            return "L'utilisateur n'a jamais fait l'exercice '" + nomExercice + "' dans son historique.";
        }

        boolean anyStd = exercises.stream().anyMatch(e -> e.getDefinition() != null);
        if (!anyStd) {
            return "L'exercice '" + nomExercice
                    + "' n'est pas dans la base standardisée [std]. Analyse de progression indisponible.";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRANCE);
        List<Exercice> stdExercises = exercises.stream().filter(e -> e.getDefinition() != null)
                .collect(Collectors.toList());
        StringBuilder res = new StringBuilder(
                "Historique complet de '" + nomExercice + "' [std] (" + stdExercises.size() + " séance(s)) :\n");

        for (Exercice exo : stdExercises) {
            String dateStr = exo.getStartTime() != null ? exo.getStartTime().format(formatter) : "Date inconnue";
            res.append("- ").append(dateStr).append(" : ");
            if (exo.getSeries() != null && !exo.getSeries().isEmpty()) {
                String seriesDetails = exo.getSeries().stream()
                        .map(s -> s.getNombreReps() + " reps @ " + s.getPoids() + "kg")
                        .collect(Collectors.joining(" | "));
                res.append(seriesDetails);
            } else {
                res.append("Aucune série enregistrée");
            }
            res.append("\n");
        }
        return res.toString();
    }

    @Tool("Trouve le record personnel d'un exercice : meilleure série réalisée et 1RM estimé (formule d'Epley).")
    public String getPersonalRecord(@ToolMemoryId String memoryId, String nomExercice) {
        List<Exercice> exercises = exerciceRepository.findAllHistoricExercises(toolUserResolver.loadId(memoryId),
                nomExercice);

        if (exercises.isEmpty()) {
            return "L'utilisateur n'a jamais fait l'exercice '" + nomExercice + "' dans son historique.";
        }

        boolean anyStdPR = exercises.stream().anyMatch(e -> e.getDefinition() != null);
        if (!anyStdPR) {
            return "L'exercice '" + nomExercice
                    + "' n'est pas dans la base standardisée [std]. Analyse de progression indisponible.";
        }
        exercises = exercises.stream().filter(e -> e.getDefinition() != null).collect(Collectors.toList());

        Serie bestSet = null;
        LocalDateTime bestDate = null;
        double best1RM = 0;

        for (Exercice exo : exercises) {
            if (exo.getSeries() == null) continue;
            for (Serie serie : exo.getSeries()) {
                double rm1 = serie.getPoids() * (1 + serie.getNombreReps() / 30.0);
                if (rm1 > best1RM) {
                    best1RM = rm1;
                    bestSet = serie;
                    bestDate = exo.getStartTime();
                }
            }
        }

        if (bestSet == null) {
            return "Aucune série enregistrée pour l'exercice '" + nomExercice + "'.";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRANCE);
        String dateStr = bestDate != null ? bestDate.format(formatter) : "date inconnue";

        return String.format(
                "Record personnel pour '%s' :\n- Meilleure série : %d reps @ %.1f kg (le %s)\n- 1RM estimé (formule d'Epley) : %.1f kg",
                nomExercice, bestSet.getNombreReps(), bestSet.getPoids(), dateStr, best1RM);
    }

    @Tool("Récupère la fiche technique d'un exercice de la bibliothèque standardisée : muscles ciblés, équipement, difficulté, exécution (en français).")
    public String getExerciceTechnique(String nomExercice) {
        if (nomExercice == null || nomExercice.isBlank()) {
            return "Nom d'exercice manquant.";
        }

        List<ExerciceDefinitionDto> results = exerciceDefinitionService.search(nomExercice, null, null, null);
        if (results.isEmpty()) {
            return "Aucun exercice standardisé trouvé pour '" + nomExercice
                    + "'. Tu peux proposer à l'utilisateur de chercher autrement.";
        }

        ExerciceDefinitionDto e = results.get(0);
        StringBuilder res = new StringBuilder();
        res.append("Fiche technique : ").append(e.nomFr() != null ? e.nomFr() : e.nomEn()).append("\n");
        if (e.musclePrincipal() != null) {
            res.append("Muscle principal : ").append(formatMuscle(e.musclePrincipal())).append("\n");
        }
        if (e.musclesSecondaires() != null && !e.musclesSecondaires().isEmpty()) {
            String secs = e.musclesSecondaires().stream()
                    .map(this::formatMuscle)
                    .collect(Collectors.joining(", "));
            res.append("Muscles secondaires : ").append(secs).append("\n");
        }
        if (e.typeEquipement() != null) {
            res.append("Équipement : ").append(formatEquipement(e.typeEquipement())).append("\n");
        }
        if (e.difficulte() != null) {
            res.append("Difficulté : ").append(formatDifficulte(e.difficulte())).append("\n");
        }
        if (e.descriptionFr() != null && !e.descriptionFr().isBlank()) {
            res.append("Exécution : ").append(e.descriptionFr().trim());
        }
        return res.toString();
    }

    @Tool("Recherche dans la bibliothèque standardisée des exercices selon des critères : groupe musculaire, équipement disponible, niveau de difficulté. Tous les paramètres sont optionnels. Valeurs muscle : PECTORAUX, DOS, EPAULES, BICEPS, TRICEPS, ABDOMINAUX, QUADRICEPS, ISCHIO_JAMBIERS, FESSIERS, MOLLETS, AVANT_BRAS, TRAPEZES, LOMBAIRES, ADDUCTEURS, ABDUCTEURS, CARDIO. Valeurs équipement : POIDS_DU_CORPS, HALTERES, BARRE, MACHINE, POULIE, KETTLEBELL, ELASTIQUE, BARRE_FIXE, ANNEAUX, AUTRE. Valeurs difficulté : DEBUTANT, INTERMEDIAIRE, AVANCE.")
    public String rechercherExercices(String muscle, String equipement, String difficulte) {
        String muscleArg = (muscle != null && !muscle.isBlank()) ? muscle.trim().toUpperCase() : null;
        String equipArg = (equipement != null && !equipement.isBlank()) ? equipement.trim().toUpperCase() : null;
        String diffArg = (difficulte != null && !difficulte.isBlank()) ? difficulte.trim().toUpperCase() : null;

        if (muscleArg == null && equipArg == null && diffArg == null) {
            return "Aucun critère fourni. Précise au moins un filtre : muscle, équipement ou difficulté.";
        }

        List<ExerciceDefinitionDto> results;
        try {
            results = exerciceDefinitionService.search(null, muscleArg, equipArg, diffArg);
        } catch (IllegalArgumentException ex) {
            return "Filtre invalide : " + ex.getMessage()
                    + ". Utilise les valeurs exactes indiquées dans la description de l'outil.";
        }

        if (results.isEmpty()) {
            return "Aucun exercice ne correspond à ces critères.";
        }

        List<ExerciceDefinitionDto> top = results.stream().limit(10).collect(Collectors.toList());
        StringBuilder res = new StringBuilder();
        res.append(top.size()).append(" exercice(s) trouvé(s)");
        if (results.size() > top.size()) {
            res.append(" (sur ").append(results.size()).append(", premiers résultats)");
        }
        res.append(" :\n");
        for (ExerciceDefinitionDto e : top) {
            res.append("- ").append(e.nomFr() != null ? e.nomFr() : e.nomEn());
            if (e.musclePrincipal() != null) {
                res.append(" — ").append(formatMuscle(e.musclePrincipal()));
            }
            if (e.typeEquipement() != null) {
                res.append(" / ").append(formatEquipement(e.typeEquipement()));
            }
            if (e.difficulte() != null) {
                res.append(" / ").append(formatDifficulte(e.difficulte()));
            }
            res.append("\n");
        }
        return res.toString();
    }

    @Tool("Crée un programme d'entraînement (modèle) complet avec ses exercices et leurs séries cibles. " +
            "Chaque nom d'exercice doit correspondre à un exercice de la bibliothèque standardisée — appelle [rechercherExercices] avant "
            +
            "pour récupérer les noms exacts. À utiliser quand l'utilisateur demande de créer / générer / construire un programme.")
    public String creerProgramme(@ToolMemoryId String memoryId, String titre, List<ProgrammeExerciceSpec> exercices) {
        Utilisateur user = toolUserResolver.load(memoryId);

        if (titre == null || titre.isBlank()) {
            return "Le titre du programme est obligatoire.";
        }
        if (exercices == null || exercices.isEmpty()) {
            return "Au moins un exercice est requis pour créer un programme.";
        }

        List<ExerciceDto> exerciceDtos = new ArrayList<>();
        List<String> introuvables = new ArrayList<>();
        List<String> resumeExos = new ArrayList<>();

        for (ProgrammeExerciceSpec spec : exercices) {
            if (spec == null || spec.nomExercice() == null || spec.nomExercice().isBlank()) {
                continue;
            }
            List<ExerciceDefinitionDto> matches = exerciceDefinitionService.search(spec.nomExercice(), null, null,
                    null);
            if (matches.isEmpty()) {
                introuvables.add(spec.nomExercice());
                continue;
            }
            ExerciceDefinitionDto def = matches.get(0);
            int nbSeries = (spec.nbSeries() != null && spec.nbSeries() > 0) ? spec.nbSeries() : 3;
            int reps = (spec.reps() != null && spec.reps() > 0) ? spec.reps() : 10;

            List<SerieDto> series = new ArrayList<>();
            for (int i = 0; i < nbSeries; i++) {
                series.add(new SerieDto(null, reps, null, null, null, null, null, null, null, null));
            }

            String nomCanonique = def.nomFr() != null ? def.nomFr() : def.nomEn();
            exerciceDtos
                    .add(new ExerciceDto(null, nomCanonique, null, def.id(), series, null, null, null, null, false));
            resumeExos.add(nomCanonique + " (" + nbSeries + "x" + reps + ")");
        }

        if (!introuvables.isEmpty()) {
            return "Impossible de créer le programme : exercices introuvables dans la bibliothèque standardisée : "
                    + String.join(", ", introuvables)
                    + ". Utilise [rechercherExercices] pour obtenir les noms exacts puis réessaie.";
        }

        if (exerciceDtos.isEmpty()) {
            return "Aucun exercice valide fourni.";
        }

        SeanceDto dto = new SeanceDto(null, titre, null, null, null, 0, false, null, exerciceDtos);
        Seance saved = programmeService.sauvegarderProgramme(user.getUsername(), dto);

        return "Programme '" + saved.getTitre() + "' créé avec " + exerciceDtos.size() + " exercice(s) : "
                + String.join(", ", resumeExos)
                + ". Il est disponible dans l'onglet Programmes.";
    }

    @Tool("Analyse la couverture musculaire des derniers jours : par groupe musculaire, nombre de séances et de séries au total, et muscles négligés.")
    public String analyserCouvertureMusculaire(@ToolMemoryId String memoryId, Integer nbJours) {
        Utilisateur user = toolUserResolver.load(memoryId);

        int window = (nbJours != null && nbJours > 0) ? nbJours : 7;
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(window);

        List<Seance> historique = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueTrueOrderByStartTimeDesc(user.getUsername())
                .stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(cutoff))
                .collect(Collectors.toList());

        if (historique.isEmpty()) {
            return "Aucune séance exécutée sur les " + window + " derniers jours.";
        }

        Map<MuscleGroup, Set<Long>> sessionsByMuscle = new EnumMap<>(MuscleGroup.class);
        Map<MuscleGroup, Integer> seriesByMuscle = new EnumMap<>(MuscleGroup.class);

        for (Seance s : historique) {
            if (s.getExercices() == null) continue;
            for (Exercice e : s.getExercices()) {
                if (e.getDefinition() == null || e.getDefinition().getMusclePrincipal() == null) continue;
                MuscleGroup m = e.getDefinition().getMusclePrincipal();
                sessionsByMuscle.computeIfAbsent(m, k -> new HashSet<>()).add(s.getId());
                int nbSeries = (e.getSeries() != null) ? e.getSeries().size() : 0;
                seriesByMuscle.merge(m, nbSeries, Integer::sum);
            }
        }

        StringBuilder res = new StringBuilder();
        res.append("Couverture musculaire sur ").append(window).append(" jours (")
                .append(historique.size()).append(" séance(s) exécutée(s)) :\n");

        if (sessionsByMuscle.isEmpty()) {
            res.append(
                    "- Aucun exercice rattaché à la bibliothèque standardisée [std]. Analyse par muscle impossible.\n");
        } else {
            List<MuscleGroup> sorted = sessionsByMuscle.keySet().stream()
                    .sorted((a, b) -> Integer.compare(
                            seriesByMuscle.getOrDefault(b, 0),
                            seriesByMuscle.getOrDefault(a, 0)))
                    .collect(Collectors.toList());

            for (MuscleGroup m : sorted) {
                res.append("- ").append(formatMuscle(m)).append(" : ")
                        .append(sessionsByMuscle.get(m).size()).append(" séance(s), ")
                        .append(seriesByMuscle.getOrDefault(m, 0)).append(" série(s)\n");
            }
        }

        List<MuscleGroup> negliges = new ArrayList<>();
        for (MuscleGroup m : MuscleGroup.values()) {
            if (!sessionsByMuscle.containsKey(m)) negliges.add(m);
        }
        if (!negliges.isEmpty()) {
            String missing = negliges.stream().map(this::formatMuscle).collect(Collectors.joining(", "));
            res.append("Muscles non travaillés sur la période : ").append(missing).append(".");
        }

        return res.toString();
    }

    @Tool("Retourne la date du dernier entraînement par groupe musculaire, du plus ancien au plus récent.")
    public String getDernierEntrainementParMuscle(@ToolMemoryId String memoryId) {
        Utilisateur user = toolUserResolver.load(memoryId);

        List<Seance> historique = seanceRepository
                .findByUtilisateurUsernameAndHistoriqueTrueOrderByStartTimeDesc(user.getUsername());

        if (historique.isEmpty()) {
            return "Aucune séance dans l'historique de l'utilisateur.";
        }

        Map<MuscleGroup, LocalDateTime> lastByMuscle = new EnumMap<>(MuscleGroup.class);

        for (Seance s : historique) {
            if (s.getStartTime() == null || s.getExercices() == null) continue;
            for (Exercice e : s.getExercices()) {
                if (e.getDefinition() == null || e.getDefinition().getMusclePrincipal() == null) continue;
                MuscleGroup m = e.getDefinition().getMusclePrincipal();
                LocalDateTime existing = lastByMuscle.get(m);
                if (existing == null || s.getStartTime().isAfter(existing)) {
                    lastByMuscle.put(m, s.getStartTime());
                }
            }
        }

        StringBuilder res = new StringBuilder("Dernier entraînement par muscle :\n");
        LocalDateTime now = LocalDateTime.now(clock);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRANCE);

        List<Map.Entry<MuscleGroup, LocalDateTime>> entries = lastByMuscle.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toList());

        for (Map.Entry<MuscleGroup, LocalDateTime> entry : entries) {
            long jours = ChronoUnit.DAYS.between(entry.getValue().toLocalDate(), now.toLocalDate());
            res.append("- ").append(formatMuscle(entry.getKey())).append(" : il y a ")
                    .append(jours).append(" jour(s) (le ").append(entry.getValue().format(formatter)).append(")\n");
        }

        List<MuscleGroup> jamais = new ArrayList<>();
        for (MuscleGroup m : MuscleGroup.values()) {
            if (!lastByMuscle.containsKey(m)) jamais.add(m);
        }
        if (!jamais.isEmpty()) {
            String missing = jamais.stream().map(this::formatMuscle).collect(Collectors.joining(", "));
            res.append("Jamais travaillés : ").append(missing).append(".");
        }

        return res.toString();
    }

    @Tool("Récupère le palier de performance (Éphèbe → Olympien) : palier global et par exercice de référence (squat, développé couché, soulevé de terre, tractions...).")
    public String getPerformanceTier(@ToolMemoryId String memoryId) {
        Utilisateur user = toolUserResolver.load(memoryId);

        PerformanceSummaryDto summary = performanceService.getSummary(user.getUsername());

        StringBuilder res = new StringBuilder();
        res.append("Palier global : ").append(summary.getOverallTier())
                .append(" (niveau ").append(summary.getOverallTierLevel())
                .append(", catégorie ").append(summary.getOverallTierCategorie()).append(")");
        if (summary.getPoidsCorps() != null) {
            res.append(" — poids de corps : ").append(summary.getPoidsCorps()).append(" kg");
        } else {
            res.append(" — poids de corps non renseigné (limite la précision)");
        }
        res.append("\nDétail par exercice :\n");

        if (summary.getExercises() == null || summary.getExercises().isEmpty()) {
            res.append("- Aucune donnée enregistrée.");
            return res.toString();
        }

        for (ExercisePerformanceDto exo : summary.getExercises()) {
            res.append("- ").append(exo.getNom()).append(" : ").append(exo.getTier())
                    .append(" (niveau ").append(exo.getTierLevel()).append(")");
            if (exo.getRm1Estime() != null) {
                res.append(", 1RM estimé ").append(exo.getRm1Estime()).append(" kg");
            }
            res.append("\n");
        }
        return res.toString();
    }

    @Tool("Récupère le profil sportif complet : âge, sexe, taille, poids, niveau d'expérience, objectif principal, fréquence visée, matériel disponible, blessures, préférences.")
    public String getUserProfileComplet(@ToolMemoryId String memoryId) {
        Utilisateur user = toolUserResolver.load(memoryId);

        if (user.getIsOnboarded() == null || !user.getIsOnboarded()) {
            return "Le profil sportif de l'utilisateur n'est pas encore renseigné. Encourage-le à compléter son profil pour des conseils plus pertinents.";
        }

        StringBuilder res = new StringBuilder("Profil sportif :\n");
        if (user.getDateNaissance() != null) {
            int age = Period.between(user.getDateNaissance(), java.time.LocalDate.now(clock)).getYears();
            res.append("- Âge : ").append(age).append(" ans\n");
        }
        if (user.getSexe() != null) res.append("- Sexe : ").append(user.getSexe().name().toLowerCase()).append("\n");
        if (user.getTailleCm() != null) res.append("- Taille : ").append(user.getTailleCm()).append(" cm\n");
        if (user.getPoidsCorps() != null) res.append("- Poids : ").append(user.getPoidsCorps()).append(" kg\n");
        if (user.getNiveauExperience() != null)
            res.append("- Niveau : ").append(user.getNiveauExperience().name().toLowerCase()).append("\n");
        if (user.getObjectifPrincipal() != null)
            res.append("- Objectif : ").append(user.getObjectifPrincipal().name().toLowerCase().replace('_', ' '))
                    .append("\n");
        if (user.getFrequenceVisee() != null)
            res.append("- Fréquence visée : ").append(user.getFrequenceVisee()).append(" séances/semaine\n");
        if (user.getMaterielDisponible() != null && !user.getMaterielDisponible().isEmpty()) {
            String mat = user.getMaterielDisponible().stream()
                    .map(t -> t.name().toLowerCase().replace('_', ' '))
                    .collect(Collectors.joining(", "));
            res.append("- Matériel disponible : ").append(mat).append("\n");
        }
        if (user.getBlessures() != null && !user.getBlessures().isBlank()) {
            res.append("- Blessures / limitations : ").append(user.getBlessures().trim()).append("\n");
        }
        if (user.getPreferences() != null && !user.getPreferences().isBlank()) {
            res.append("- Préférences : ").append(user.getPreferences().trim()).append("\n");
        }
        return res.toString();
    }

    private String formatMuscle(MuscleGroup m) {
        return m.name().toLowerCase().replace('_', ' ');
    }

    private String formatEquipement(TypeEquipement t) {
        return t.name().toLowerCase().replace('_', ' ');
    }

    private String formatDifficulte(NiveauDifficulte d) {
        return d.name().toLowerCase();
    }

    private Exercice resolveExercice(Seance seance, String nomExercice) {
        if (nomExercice == null || nomExercice.isBlank()) {
            return seance.getExercices().get(seance.getExercices().size() - 1);
        }
        String lower = nomExercice.toLowerCase();
        for (int i = seance.getExercices().size() - 1; i >= 0; i--) {
            if (seance.getExercices().get(i).getNom().toLowerCase().contains(lower)) {
                return seance.getExercices().get(i);
            }
        }
        return seance.getExercices().get(seance.getExercices().size() - 1);
    }

    private Seance getActiveSeance(String memoryId) {
        Long pinnedId = voiceSessionContext.getPinnedSeanceId();
        if (pinnedId != null) {
            return seanceRepository.findById(pinnedId).orElse(null);
        }
        return seanceRepository
                .findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(toolUserResolver.loadId(memoryId))
                .orElse(null);
    }

    private String exoLabel(Exercice e) {
        String base = e.getDefinition() != null ? e.getNom() + " [std]" : e.getNom();
        return e.isUnilateral() ? base + " (unilatéral)" : base;
    }
}
