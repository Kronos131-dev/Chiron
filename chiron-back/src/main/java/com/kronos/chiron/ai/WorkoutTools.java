package com.kronos.chiron.ai;

import com.kronos.chiron.entity.Exercice;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.entity.Serie;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.repository.ExerciceRepository;
import com.kronos.chiron.repository.SeanceRepository;
import com.kronos.chiron.repository.UtilisateurRepository;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Component providing a suite of tools accessible by the AI agent.
 * These tools allow the AI to read and modify workout data, manage sessions,
 * and fetch historical performances on behalf of the user.
 */
@Component
@RequiredArgsConstructor
public class WorkoutTools {

    private final SeanceRepository seanceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ExerciceRepository exerciceRepository;

    /**
     * Retrieves the current system date and time.
     *
     * @return A formatted string representing the current date and time.
     */
    @Tool("Retourne la date et l'heure actuelles, ainsi que le jour de la semaine. Utilise cet outil à CHAQUE FOIS que l'utilisateur fait référence à 'aujourd'hui', 'hier', 'demain' ou s'il demande la date.")
    public String getCurrentDate() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy à HH:mm", Locale.FRANCE);
        return "Nous sommes le " + now.format(formatter);
    }

    /**
     * Retrieves the list of workout templates (presets) for the specified user or a target user.
     * Enforces privacy and role-based access controls.
     *
     * @param userId         The ID of the requesting user.
     * @param targetUsername The username of the target user to inspect (can be null to inspect self).
     * @return A formatted string listing the templates and their exercises.
     */
    @Tool("Récupère la liste des modèles de programmes d'entraînement (presets) de l'utilisateur ou d'un autre utilisateur spécifié.")
    public String getUserProgrammes(@MemoryId String userId, String targetUsername) {
        Utilisateur requestUser = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur requérant introuvable"));

        String searchUsername = (targetUsername != null && !targetUsername.isBlank()) ? targetUsername : requestUser.getUsername();

        Utilisateur targetUser = utilisateurRepository.findByUsername(searchUsername)
                .orElse(null);

        if (targetUser == null) {
            return "L'utilisateur '" + searchUsername + "' est introuvable.";
        }

        if (!requestUser.getUsername().equals(searchUsername) && requestUser.getRole() != Role.ADMIN) {
            if (targetUser.getIsPublic() == null || !targetUser.getIsPublic()) {
                return "Le profil de l'utilisateur '" + searchUsername + "' est privé. Vous n'avez pas l'autorisation de voir ses programmes.";
            }
        }

        List<Seance> programmes = seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(targetUser.getUsername());
        
        if (programmes.isEmpty()) {
            return "L'utilisateur " + searchUsername + " n'a aucun modèle de programme d'entraînement enregistré.";
        }

        StringBuilder res = new StringBuilder("L'utilisateur " + searchUsername + " a " + programmes.size() + " programmes enregistrés :\n");
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

    /**
     * Retrieves the full or recent history of executed workout sessions for a user.
     * Enforces privacy and role-based access controls.
     *
     * @param userId         The ID of the requesting user.
     * @param targetUsername The username of the target user to inspect (can be null to inspect self).
     * @return A formatted string listing historical sessions.
     */
    @Tool("Récupère l'historique complet ou récent des séances réellement effectuées par l'utilisateur ou un autre utilisateur spécifié.")
    public String getUserHistory(@MemoryId String userId, String targetUsername) {
        Utilisateur requestUser = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur requérant introuvable"));

        String searchUsername = (targetUsername != null && !targetUsername.isBlank()) ? targetUsername : requestUser.getUsername();

        Utilisateur targetUser = utilisateurRepository.findByUsername(searchUsername)
                .orElse(null);

        if (targetUser == null) {
            return "L'utilisateur '" + searchUsername + "' est introuvable.";
        }

        if (!requestUser.getUsername().equals(searchUsername) && requestUser.getRole() != Role.ADMIN) {
            if (targetUser.getIsPublic() == null || !targetUser.getIsPublic()) {
                return "Le profil de l'utilisateur '" + searchUsername + "' est privé. Vous n'avez pas l'autorisation de voir son historique.";
            }
        }

        List<Seance> historique = seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(targetUser.getUsername());
        
        if (historique.isEmpty()) {
            return "L'utilisateur " + searchUsername + " n'a encore enregistré aucune séance dans son historique.";
        }

        StringBuilder res = new StringBuilder("Voici l'historique des séances effectuées par l'utilisateur " + searchUsername + " :\n");
        for (Seance s : historique) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String dateStr = s.getStartTime() != null ? s.getStartTime().format(formatter) : "Date inconnue";
            
            res.append("- Le ").append(dateStr).append(" : Séance '").append(s.getTitre() != null ? s.getTitre() : "Sans nom").append("' (");
            
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

    /**
     * Initializes a new active workout session for the user.
     * Ends any previously uncompleted active sessions.
     *
     * @param userId The ID of the user starting the session.
     * @param titre  The title of the new session.
     * @return A status message indicating successful session creation.
     */
    @Tool("Démarre une nouvelle séance d'entraînement dans l'historique de l'utilisateur. À utiliser quand l'utilisateur annonce qu'il commence le sport.")
    public String startSession(@MemoryId String userId, String titre) {
        Utilisateur user = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(user.getId())
                .ifPresent(s -> {
                    s.setEndTime(LocalDateTime.now());
                    seanceRepository.save(s);
                });

        int currentWeek = LocalDate.now().get(WeekFields.of(Locale.FRANCE).weekOfWeekBasedYear());

        Seance seance = Seance.builder()
                .titre(titre)
                .startTime(LocalDateTime.now())
                .weekNumber(currentWeek)
                .isModele(true)
                .utilisateur(user)
                .build();

        if (seance.getExercices() == null) seance.setExercices(new ArrayList<>());

        seanceRepository.save(seance);
        return "Séance '" + titre + "' démarrée en base de données. Tu peux maintenant utiliser [startExercise].";
    }

    /**
     * Creates a new empty workout template (preset model).
     *
     * @param userId The ID of the user creating the template.
     * @param titre  The title of the new template.
     * @return A status message indicating successful template creation.
     */
    @Tool("Crée un NOUVEAU modèle de programme d'entraînement (preset) vide. À utiliser uniquement quand l'utilisateur demande explicitement à créer un programme/modèle.")
    public String createProgramModel(@MemoryId String userId, String titre) {
        Utilisateur user = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        Seance seance = Seance.builder()
                .titre(titre)
                .startTime(LocalDateTime.now())
                .weekNumber(0)
                .isModele(false)
                .utilisateur(user)
                .build();

        if (seance.getExercices() == null) seance.setExercices(new ArrayList<>());

        seanceRepository.save(seance);
        return "Modèle de programme '" + titre + "' créé avec succès. Dis à l'utilisateur qu'il peut le modifier depuis l'interface 'Programme'.";
    }

    /**
     * Starts a new exercise within the current active session.
     *
     * @param userId      The ID of the user.
     * @param nomExercice The name of the exercise being started.
     * @return A status message or error if no session is active.
     */
    @Tool("Démarre un nouvel exercice dans la séance en cours. À utiliser quand l'utilisateur change d'atelier.")
    public String startExercise(@MemoryId String userId, String nomExercice) {
        Seance activeSeance = getActiveSeance(userId);

        if (activeSeance == null) {
            return "ERREUR SYSTEME : Impossible d'ajouter l'exercice. L'utilisateur doit d'abord démarrer une séance. Appelle l'outil [startSession] en premier !";
        }

        if (activeSeance.getExercices() == null) activeSeance.setExercices(new ArrayList<>());

        int nextPosition = activeSeance.getExercices().stream()
                .mapToInt(Exercice::getDisplayOrder)
                .max()
                .orElse(-1) + 1;

        Exercice exercice = Exercice.builder()
                .nom(nomExercice)
                .startTime(LocalDateTime.now())
                .displayOrder(nextPosition)
                .build();

        if (exercice.getSeries() == null) exercice.setSeries(new ArrayList<>());

        activeSeance.addExercice(exercice);
        seanceRepository.save(activeSeance);
        return "Exercice '" + nomExercice + "' ajouté à la séance. Tu peux maintenant utiliser [addSet] pour cet exercice.";
    }

    /**
     * Records a new set (serie) for the current active exercise.
     *
     * @param userId      The ID of the user.
     * @param poids       The weight used.
     * @param reps        The number of repetitions.
     * @param commentaire Optional comments for the set.
     * @return A status message or error if constraints are not met.
     */
    @Tool("Enregistre une série (poids, répétitions, commentaire) pour l'exercice en cours.")
    public String addSet(@MemoryId String userId, double poids, int reps, String commentaire) {
        Seance activeSeance = getActiveSeance(userId);

        if (activeSeance == null) {
            return "ERREUR SYSTEME : Aucune séance n'existe. Tu dois appeler [startSession] puis [startExercise] d'abord !";
        }

        if (activeSeance.getExercices() == null || activeSeance.getExercices().isEmpty()) {
            return "ERREUR SYSTEME : Aucun exercice n'est en cours. Tu dois appeler l'outil [startExercise] d'abord !";
        }

        Exercice activeExercice = activeSeance.getExercices().get(activeSeance.getExercices().size() - 1);

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

    /**
     * Ends the current active session.
     *
     * @param userId The ID of the user finishing their workout.
     * @return A status message confirming session completion.
     */
    @Tool("Termine la séance d'entraînement en cours. À utiliser quand l'utilisateur dit qu'il a fini.")
    public String endSession(@MemoryId String userId) {
        Seance activeSeance = getActiveSeance(userId);

        if (activeSeance == null) {
            return "L'utilisateur a demandé à terminer, mais il n'y a aucune séance en cours.";
        }

        activeSeance.setEndTime(LocalDateTime.now());

        if (activeSeance.getExercices() != null && !activeSeance.getExercices().isEmpty()) {
            Exercice lastExo = activeSeance.getExercices().get(activeSeance.getExercices().size() - 1);
            if (lastExo.getEndTime() == null) {
                lastExo.setEndTime(LocalDateTime.now());
            }
        }

        seanceRepository.save(activeSeance);
        return "Séance terminée et sauvegardée avec succès dans l'historique.";
    }

    /**
     * Fetches the user's most recent historical performance for a specific exercise.
     *
     * @param userId      The ID of the user.
     * @param nomExercice The name of the exercise to search for.
     * @return A formatted string detailing the previous sets and performance.
     */
    @Tool("Récupère les performances de la DERNIÈRE FOIS que l'utilisateur a fait un exercice spécifique.")
    public String getLastExercisePerformance(@MemoryId String userId, String nomExercice) {
        Optional<Exercice> lastExoOpt = exerciceRepository
                .findFirstHistoricExercise(Long.parseLong(userId), nomExercice);

        if (lastExoOpt.isEmpty()) {
            return "Information pour l'IA : L'utilisateur n'a jamais fait l'exercice '" + nomExercice + "' dans son historique.";
        }

        Exercice exo = lastExoOpt.get();
        if (exo.getDefinition() == null) {
            return "L'exercice '" + nomExercice + "' n'est pas dans la base standardisée [std]. Analyse de progression indisponible.";
        }

        StringBuilder reponse = new StringBuilder();

        if (exo.getStartTime() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy à HH:mm", Locale.FRANCE);
            reponse.append("Dernière performance trouvée le ").append(exo.getStartTime().format(formatter)).append(".\n");
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

    /**
     * Searches for user profiles matching a specific query string.
     * Restricted to users with the ADMIN role.
     *
     * @param userId The ID of the admin user.
     * @param query  The search string.
     * @return A formatted list of matching profiles, or an access denial message.
     */
    @Tool("Recherche les profils utilisateurs existants (utile pour un admin qui cherche quelqu'un).")
    public String searchAllProfiles(@MemoryId String userId, String query) {
        Utilisateur requestUser = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur requérant introuvable"));

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

    /**
     * Generates a detailed summary of workouts performed on a specific date.
     *
     * @param userId  The ID of the user.
     * @param dateStr The target date in YYYY-MM-DD format.
     * @return A formatted summary of exercises and sets for that day.
     */
    @Tool("Récupère un résumé détaillé des séances de sport effectuées à une date précise (format attendu YYYY-MM-DD).")
    public String getWorkoutSummaryByDate(@MemoryId String userId, String dateStr) {
        Utilisateur user = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
                
        List<Seance> historique = seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername());
        
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

    /**
     * Returns the full details (exercises, sets, weights, reps) of a specific programme by name.
     *
     * @param userId        The ID of the requesting user.
     * @param programmeName The name (or partial name) of the programme to fetch.
     * @return A formatted string with all exercises and their sets for the matching programme(s).
     */
    @Tool("Récupère le contenu détaillé d'un programme spécifique (exercices, séries, poids, répétitions) à partir de son nom. À utiliser quand l'utilisateur demande 'qu'est-ce que mon programme X contient ?'.")
    public String getProgrammeDetails(@MemoryId String userId, String programmeName) {
        Utilisateur user = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        List<Seance> programmes = seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(user.getUsername());

        List<Seance> matching = programmes.stream()
                .filter(s -> s.getTitre() != null && s.getTitre().toLowerCase().contains(programmeName.toLowerCase()))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            return "Aucun programme trouvé avec le nom '" + programmeName + "'. Utilise [getUserProgrammes] pour voir les programmes disponibles.";
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

    /**
     * Returns the full details of a specific historical session found by title.
     *
     * @param userId       The ID of the user.
     * @param sessionTitle The title (or partial title) of the session to look up.
     * @return A formatted string with the date, exercises, sets, and comments for the matching session(s).
     */
    @Tool("Récupère les détails complets d'une ou plusieurs séances historiques à partir de leur titre. Inclut tous les exercices, séries, poids et commentaires. À utiliser quand l'utilisateur mentionne le nom d'une séance.")
    public String getSessionDetails(@MemoryId String userId, String sessionTitle) {
        Utilisateur user = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        List<Seance> historique = seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername());

        List<Seance> matching = historique.stream()
                .filter(s -> s.getTitre() != null && s.getTitre().toLowerCase().contains(sessionTitle.toLowerCase()))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            return "Aucune séance trouvée avec le titre '" + sessionTitle + "'. Utilise [getUserHistory] pour voir l'historique complet.";
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

    /**
     * Returns the complete history of every time the user performed a specific exercise,
     * with all sets, weights and reps from each session.
     *
     * @param userId      The ID of the user.
     * @param nomExercice The name (or partial name) of the exercise.
     * @return A formatted string listing every historical occurrence of the exercise.
     */
    @Tool("Récupère l'historique COMPLET de toutes les fois où l'utilisateur a effectué un exercice donné, avec les détails de chaque série. À utiliser pour analyser la progression sur un exercice.")
    public String getFullExerciseHistory(@MemoryId String userId, String nomExercice) {
        List<Exercice> exercises = exerciceRepository.findAllHistoricExercises(Long.parseLong(userId), nomExercice);

        if (exercises.isEmpty()) {
            return "L'utilisateur n'a jamais fait l'exercice '" + nomExercice + "' dans son historique.";
        }

        boolean anyStd = exercises.stream().anyMatch(e -> e.getDefinition() != null);
        if (!anyStd) {
            return "L'exercice '" + nomExercice + "' n'est pas dans la base standardisée [std]. Analyse de progression indisponible.";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRANCE);
        List<Exercice> stdExercises = exercises.stream().filter(e -> e.getDefinition() != null).collect(Collectors.toList());
        StringBuilder res = new StringBuilder("Historique complet de '" + nomExercice + "' [std] (" + stdExercises.size() + " séance(s)) :\n");

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

    /**
     * Finds the personal record (best estimated 1RM and heaviest set) for a specific exercise.
     * Uses the Epley formula: 1RM = weight × (1 + reps / 30).
     *
     * @param userId      The ID of the user.
     * @param nomExercice The name (or partial name) of the exercise.
     * @return A formatted string with the best set and estimated 1RM.
     */
    @Tool("Trouve le record personnel de l'utilisateur pour un exercice donné : la meilleure série réalisée et le 1RM estimé (formule d'Epley). À utiliser quand l'utilisateur demande son PR ou son record.")
    public String getPersonalRecord(@MemoryId String userId, String nomExercice) {
        List<Exercice> exercises = exerciceRepository.findAllHistoricExercises(Long.parseLong(userId), nomExercice);

        if (exercises.isEmpty()) {
            return "L'utilisateur n'a jamais fait l'exercice '" + nomExercice + "' dans son historique.";
        }

        boolean anyStdPR = exercises.stream().anyMatch(e -> e.getDefinition() != null);
        if (!anyStdPR) {
            return "L'exercice '" + nomExercice + "' n'est pas dans la base standardisée [std]. Analyse de progression indisponible.";
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

    /**
     * Utility method to fetch the current unfinished active session for the given user.
     *
     * @param userId The ID of the user as a string.
     * @return The active Seance entity, or null if none exists.
     */
    private Seance getActiveSeance(String userId) {
        return seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(Long.parseLong(userId))
                .orElse(null);
    }

    private String exoLabel(Exercice e) {
        return e.getDefinition() != null ? e.getNom() + " [std]" : e.getNom();
    }
}
