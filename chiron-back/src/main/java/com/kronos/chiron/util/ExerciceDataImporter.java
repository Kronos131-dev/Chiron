package com.kronos.chiron.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.kronos.chiron.entity.*;
import com.kronos.chiron.repository.ExerciceDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Importe les exercices depuis le dataset free-exercise-db (github.com/wrkout/exercises.json).
 *
 * Format JSON attendu :
 * {
 *   "id": "Barbell_Squat",
 *   "name": "Barbell Squat",
 *   "level": "intermediate",
 *   "equipment": "barbell",
 *   "primaryMuscles": ["quadriceps"],
 *   "secondaryMuscles": ["glutes", "hamstrings"],
 *   "instructions": ["..."],
 *   "category": "strength",
 *   "images": ["Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"]
 * }
 *
 * Les images sont des chemins relatifs au dossier imageDir.
 * Elles sont copiées dans UPLOADS_DIR/exercices/{exerciceId}/.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExerciceDataImporter {

    private final ExerciceDefinitionRepository repository;
    private final JsonMapper objectMapper;

    @Value("${chiron.uploads-dir:./uploads/images}")
    private String uploadsDir;

    // ──────────────────────────────────────────────────
    // Mapping niveau → NiveauDifficulte
    // ──────────────────────────────────────────────────
    static final Map<String, NiveauDifficulte> LEVEL_MAP = Map.of(
            "beginner", NiveauDifficulte.DEBUTANT,
            "intermediate", NiveauDifficulte.INTERMEDIAIRE,
            "expert", NiveauDifficulte.AVANCE
    );

    // ──────────────────────────────────────────────────
    // Mapping equipment → TypeEquipement
    // ──────────────────────────────────────────────────
    static final Map<String, TypeEquipement> EQUIPMENT_MAP = Map.ofEntries(
            Map.entry("barbell", TypeEquipement.BARRE),
            Map.entry("dumbbell", TypeEquipement.HALTERES),
            Map.entry("cable", TypeEquipement.POULIE),
            Map.entry("machine", TypeEquipement.MACHINE),
            Map.entry("kettlebells", TypeEquipement.KETTLEBELL),
            Map.entry("bands", TypeEquipement.ELASTIQUE),
            Map.entry("body only", TypeEquipement.POIDS_DU_CORPS),
            Map.entry("e-z curl bar", TypeEquipement.BARRE),
            Map.entry("medicine ball", TypeEquipement.AUTRE),
            Map.entry("exercise ball", TypeEquipement.AUTRE),
            Map.entry("foam roll", TypeEquipement.AUTRE),
            Map.entry("other", TypeEquipement.AUTRE)
    );

    // ──────────────────────────────────────────────────
    // Mapping muscle → MuscleGroup
    // ──────────────────────────────────────────────────
    static final Map<String, MuscleGroup> MUSCLE_MAP = Map.ofEntries(
            Map.entry("abdominals", MuscleGroup.ABDOMINAUX),
            Map.entry("abductors", MuscleGroup.ABDUCTEURS),
            Map.entry("adductors", MuscleGroup.ADDUCTEURS),
            Map.entry("biceps", MuscleGroup.BICEPS),
            Map.entry("calves", MuscleGroup.MOLLETS),
            Map.entry("chest", MuscleGroup.PECTORAUX),
            Map.entry("forearms", MuscleGroup.AVANT_BRAS),
            Map.entry("glutes", MuscleGroup.FESSIERS),
            Map.entry("hamstrings", MuscleGroup.ISCHIO_JAMBIERS),
            Map.entry("lats", MuscleGroup.DOS),
            Map.entry("lower back", MuscleGroup.LOMBAIRES),
            Map.entry("middle back", MuscleGroup.DOS),
            Map.entry("neck", MuscleGroup.TRAPEZES),
            Map.entry("quadriceps", MuscleGroup.QUADRICEPS),
            Map.entry("shoulders", MuscleGroup.EPAULES),
            Map.entry("traps", MuscleGroup.TRAPEZES),
            Map.entry("triceps", MuscleGroup.TRICEPS)
    );

    // ──────────────────────────────────────────────────────────────────────────
    // Traductions françaises — clé = nom anglais en minuscules
    // Couvre la quasi-totalité des 873 exercices du dataset free-exercise-db
    // ──────────────────────────────────────────────────────────────────────────
    static final Map<String, String> FR_NAMES;

    static {
        FR_NAMES = new HashMap<>();

        // ── Sit-ups / Abdos ────────────────────────────────────────────────
        FR_NAMES.put("3/4 sit-up", "Relevé de buste 3/4");
        FR_NAMES.put("ab crunch machine", "Machine crunch abdominaux");
        FR_NAMES.put("ab roller", "Roue abdominale");
        FR_NAMES.put("alternate heel touchers", "Touchers de talon alternés");
        FR_NAMES.put("bent-knee hip raise", "Relevé de hanches genoux fléchis");
        FR_NAMES.put("bosu ball cable crunch with side bends", "Crunch poulie sur bosu avec inclinaison latérale");
        FR_NAMES.put("butt-ups", "Relevé de bassin");
        FR_NAMES.put("cocoons", "Cocooning abdominal");
        FR_NAMES.put("crunch - hands overhead", "Crunch mains au-dessus de la tête");
        FR_NAMES.put("crunch - legs on exercise ball", "Crunch jambes sur ballon");
        FR_NAMES.put("crunches", "Crunchs");
        FR_NAMES.put("cross-body crunch", "Crunch croisé");
        FR_NAMES.put("cable crunch", "Crunch poulie");
        FR_NAMES.put("cable reverse crunch", "Crunch inversé poulie");
        FR_NAMES.put("cable russian twists", "Torsions russes à la poulie");
        FR_NAMES.put("cable seated crunch", "Crunch assis à la poulie");
        FR_NAMES.put("decline crunch", "Crunch décliné");
        FR_NAMES.put("decline oblique crunch", "Crunch oblique décliné");
        FR_NAMES.put("decline reverse crunch", "Crunch inversé décliné");
        FR_NAMES.put("dead bug", "Dead bug");
        FR_NAMES.put("flat bench leg pull-in", "Tirage de jambes sur banc plat");
        FR_NAMES.put("flat bench lying leg raise", "Relevé de jambes allongé sur banc");
        FR_NAMES.put("flutter kicks", "Battements de jambes");
        FR_NAMES.put("frog sit-ups", "Relevé de buste grenouille");
        FR_NAMES.put("gorilla chin/crunch", "Traction crunch gorille");
        FR_NAMES.put("hanging leg raise", "Relevé de jambes suspendu");
        FR_NAMES.put("hanging pike", "Pike suspendu");
        FR_NAMES.put("jackknife sit-up", "Relevé de buste couteau de poche");
        FR_NAMES.put("janda sit-up", "Relevé de buste Janda");
        FR_NAMES.put("knee/hip raise on parallel bars", "Relevé de genoux/hanches aux barres parallèles");
        FR_NAMES.put("kneeling cable crunch with alternating oblique twists", "Crunch poulie à genoux avec torsion oblique");
        FR_NAMES.put("leg pull-in", "Tirage de jambes");
        FR_NAMES.put("lying crossover", "Croisé allongé");
        FR_NAMES.put("oblique crunches", "Crunchs obliques");
        FR_NAMES.put("oblique crunches - on the floor", "Crunchs obliques au sol");
        FR_NAMES.put("otis-up", "Relevé de buste Otis");
        FR_NAMES.put("pallof press", "Pallof press");
        FR_NAMES.put("pallof press with rotation", "Pallof press avec rotation");
        FR_NAMES.put("plank", "Gainage");
        FR_NAMES.put("press sit-up", "Relevé de buste avec développé");
        FR_NAMES.put("reverse crunch", "Crunch inversé");
        FR_NAMES.put("rope crunch", "Crunch à la corde");
        FR_NAMES.put("russian twist", "Torsion russe");
        FR_NAMES.put("scissor kick", "Ciseaux");
        FR_NAMES.put("seated barbell twist", "Torsion barre assise");
        FR_NAMES.put("seated flat bench leg pull-in", "Tirage de jambes assis sur banc");
        FR_NAMES.put("seated leg tucks", "Relevé de genoux assis");
        FR_NAMES.put("side bridge", "Gainage latéral");
        FR_NAMES.put("side jackknife", "Couteau de poche latéral");
        FR_NAMES.put("sit-up", "Relevé de buste");
        FR_NAMES.put("stomach vacuum", "Vacuum abdominal");
        FR_NAMES.put("suspended reverse crunch", "Crunch inversé suspendu");
        FR_NAMES.put("toe touchers", "Touchers d'orteils");
        FR_NAMES.put("tuck crunch", "Crunch genoux ramenés");
        FR_NAMES.put("weighted crunches", "Crunchs lestés");
        FR_NAMES.put("weighted sit-ups - with bands", "Relevés de buste lestés avec élastiques");
        FR_NAMES.put("lower back curl", "Curl bas du dos");
        FR_NAMES.put("leg-over floor press", "Développé sol jambe croisée");

        // ── Squat & Jambes ──────────────────────────────────────────────────
        FR_NAMES.put("barbell full squat", "Squat complet barre");
        FR_NAMES.put("barbell hack squat", "Hack squat barre");
        FR_NAMES.put("barbell squat", "Squat barre");
        FR_NAMES.put("barbell squat to a bench", "Squat barre sur banc");
        FR_NAMES.put("bodyweight squat", "Squat poids du corps");
        FR_NAMES.put("bodyweight walking lunge", "Fente marchée poids du corps");
        FR_NAMES.put("box squat", "Squat sur boîte");
        FR_NAMES.put("box squat with bands", "Squat sur boîte avec élastiques");
        FR_NAMES.put("box squat with chains", "Squat sur boîte avec chaînes");
        FR_NAMES.put("chair squat", "Squat sur chaise");
        FR_NAMES.put("crossover reverse lunge", "Fente croisée inversée");
        FR_NAMES.put("dumbbell lunges", "Fentes haltères");
        FR_NAMES.put("dumbbell rear lunge", "Fente arrière haltères");
        FR_NAMES.put("dumbbell squat", "Squat haltères");
        FR_NAMES.put("dumbbell squat to a bench", "Squat haltères sur banc");
        FR_NAMES.put("dumbbell step ups", "Montées sur banc haltères");
        FR_NAMES.put("elevated back lunge", "Fente arrière surélevée");
        FR_NAMES.put("frankenstein squat", "Squat Frankenstein");
        FR_NAMES.put("freehand jump squat", "Squat sauté poids du corps");
        FR_NAMES.put("front barbell squat", "Squat avant barre");
        FR_NAMES.put("front barbell squat to a bench", "Squat avant barre sur banc");
        FR_NAMES.put("front squat (clean grip)", "Squat avant prise olympique");
        FR_NAMES.put("front squats with two kettlebells", "Squat avant deux kettlebells");
        FR_NAMES.put("goblet squat", "Goblet squat");
        FR_NAMES.put("hack squat", "Hack squat");
        FR_NAMES.put("jefferson squats", "Squat Jefferson");
        FR_NAMES.put("jerk dip squat", "Squat fente jeté");
        FR_NAMES.put("kettlebell pistol squat", "Squat pistol kettlebell");
        FR_NAMES.put("kneeling jump squat", "Squat sauté à genoux");
        FR_NAMES.put("kneeling squat", "Squat à genoux");
        FR_NAMES.put("leg extensions", "Extensions quadriceps");
        FR_NAMES.put("leg press", "Presse à cuisses");
        FR_NAMES.put("lying machine squat", "Squat machine allongé");
        FR_NAMES.put("narrow stance hack squats", "Hack squat pieds serrés");
        FR_NAMES.put("narrow stance leg press", "Presse à cuisses pieds serrés");
        FR_NAMES.put("narrow stance squats", "Squats pieds serrés");
        FR_NAMES.put("olympic squat", "Squat olympique");
        FR_NAMES.put("one leg barbell squat", "Squat unilatéral barre");
        FR_NAMES.put("one-arm overhead kettlebell squats", "Squat overhead unilatéral kettlebell");
        FR_NAMES.put("overhead squat", "Squat overhead");
        FR_NAMES.put("plie dumbbell squat", "Squat plié haltères");
        FR_NAMES.put("single-leg high box squat", "Squat unilatéral boîte haute");
        FR_NAMES.put("sit squats", "Squats assis");
        FR_NAMES.put("smith machine leg press", "Presse à cuisses Smith");
        FR_NAMES.put("smith machine pistol squat", "Squat pistol Smith");
        FR_NAMES.put("smith machine squat", "Squat Smith");
        FR_NAMES.put("smith single-leg split squat", "Squat fendu unilatéral Smith");
        FR_NAMES.put("speed box squat", "Squat explosif sur boîte");
        FR_NAMES.put("speed squats", "Squats explosifs");
        FR_NAMES.put("split squat with dumbbells", "Squat fendu haltères");
        FR_NAMES.put("split squats", "Squats fendus");
        FR_NAMES.put("squat jerk", "Squat jeté");
        FR_NAMES.put("squat with bands", "Squat avec élastiques");
        FR_NAMES.put("squat with chains", "Squat avec chaînes");
        FR_NAMES.put("squats - with bands", "Squats avec élastiques");
        FR_NAMES.put("step-up with knee raise", "Montée sur banc avec relevé de genou");
        FR_NAMES.put("suspended split squat", "Squat fendu suspendu");
        FR_NAMES.put("barbell lunge", "Fente barre");
        FR_NAMES.put("barbell walking lunge", "Fente marchée barre");
        FR_NAMES.put("barbell step ups", "Montées sur banc barre");
        FR_NAMES.put("barbell side split squat", "Squat latéral barre");
        FR_NAMES.put("wide stance barbell squat", "Squat barre pieds écartés");
        FR_NAMES.put("weighted jump squat", "Squat sauté lesté");
        FR_NAMES.put("weighted squat", "Squat lesté");
        FR_NAMES.put("smith machine hang power clean", "Épaulé de force suspendu Smith");
        FR_NAMES.put("zercher squats", "Squat Zercher");

        // ── Ischio-jambiers / Fessiers ─────────────────────────────────────
        FR_NAMES.put("90/90 hamstring", "Étirement ischio 90/90");
        FR_NAMES.put("ball leg curl", "Curl ischio sur ballon");
        FR_NAMES.put("barbell glute bridge", "Hip bridge barre");
        FR_NAMES.put("barbell hip thrust", "Hip thrust barre");
        FR_NAMES.put("butt lift (bridge)", "Pont fessier");
        FR_NAMES.put("cable hip adduction", "Adduction de hanche poulie");
        FR_NAMES.put("donkey calf raises", "Relevé de mollets âne");
        FR_NAMES.put("glute ham raise", "Curl ischios sur banc GHR");
        FR_NAMES.put("glute kickback", "Kickback fessier");
        FR_NAMES.put("hip extension with bands", "Extension de hanche avec élastiques");
        FR_NAMES.put("lying leg curls", "Curl ischio allongé machine");
        FR_NAMES.put("natural glute ham raise", "Curl ischios naturel");
        FR_NAMES.put("pelvic tilt into bridge", "Pont avec bascule pelvienne");
        FR_NAMES.put("physioball hip bridge", "Hip bridge sur ballon");
        FR_NAMES.put("platform hamstring slides", "Glissements ischio sur plateau");
        FR_NAMES.put("pull through", "Pull-through");
        FR_NAMES.put("rear leg raises", "Relevé de jambe arrière");
        FR_NAMES.put("reverse hyperextension", "Hyperextension inversée");
        FR_NAMES.put("seated band hamstring curl", "Curl ischio assis avec élastique");
        FR_NAMES.put("seated leg curl", "Curl ischio assis machine");
        FR_NAMES.put("single leg glute bridge", "Pont fessier unilatéral");
        FR_NAMES.put("wide stance stiff legs", "Jambes tendues pieds écartés");
        FR_NAMES.put("floor glute-ham raise", "Curl ischios au sol");
        FR_NAMES.put("donkey calf raises", "Relevé de mollets âne");
        FR_NAMES.put("prone manual hamstring", "Curl ischio manuel allongé");

        // ── Soulevé de terre ───────────────────────────────────────────────
        FR_NAMES.put("axle deadlift", "Soulevé de terre barre axle");
        FR_NAMES.put("barbell deadlift", "Soulevé de terre barre");
        FR_NAMES.put("cable deadlifts", "Soulevé de terre poulie");
        FR_NAMES.put("car deadlift", "Soulevé de terre voiture");
        FR_NAMES.put("clean deadlift", "Soulevé de terre épaulé");
        FR_NAMES.put("deadlift with bands", "Soulevé de terre avec élastiques");
        FR_NAMES.put("deadlift with chains", "Soulevé de terre avec chaînes");
        FR_NAMES.put("deficit deadlift", "Soulevé de terre en déficit");
        FR_NAMES.put("kettlebell one-legged deadlift", "Soulevé de terre unilatéral kettlebell");
        FR_NAMES.put("leverage deadlift", "Soulevé de terre machine levier");
        FR_NAMES.put("one-arm side deadlift", "Soulevé de terre latéral unilatéral");
        FR_NAMES.put("rack pull with bands", "Rack pull avec élastiques");
        FR_NAMES.put("rack pulls", "Rack pulls");
        FR_NAMES.put("reverse band deadlift", "Soulevé de terre bande inversée");
        FR_NAMES.put("reverse band sumo deadlift", "Soulevé de terre sumo bande inversée");
        FR_NAMES.put("rickshaw deadlift", "Soulevé de terre rickshaw");
        FR_NAMES.put("romanian deadlift", "Soulevé de terre roumain");
        FR_NAMES.put("romanian deadlift from deficit", "Soulevé de terre roumain en déficit");
        FR_NAMES.put("snatch deadlift", "Soulevé de terre arraché");
        FR_NAMES.put("stiff-legged barbell deadlift", "Soulevé de terre jambes tendues barre");
        FR_NAMES.put("stiff-legged dumbbell deadlift", "Soulevé de terre jambes tendues haltères");
        FR_NAMES.put("stiff leg barbell good morning", "Good morning jambes tendues barre");
        FR_NAMES.put("sumo deadlift", "Soulevé de terre sumo");
        FR_NAMES.put("sumo deadlift with bands", "Soulevé de terre sumo avec élastiques");
        FR_NAMES.put("sumo deadlift with chains", "Soulevé de terre sumo avec chaînes");
        FR_NAMES.put("trap bar deadlift", "Soulevé de terre barre hexagonale");
        FR_NAMES.put("smith machine stiff-legged deadlift", "Soulevé de terre jambes tendues Smith");
        FR_NAMES.put("leverage deadlift", "Soulevé de terre machine levier");

        // ── Développé couché & Poitrine ────────────────────────────────────
        FR_NAMES.put("barbell bench press - medium grip", "Développé couché barre prise moyenne");
        FR_NAMES.put("barbell guillotine bench press", "Développé guillotine barre");
        FR_NAMES.put("barbell incline bench press - medium grip", "Développé incliné barre prise moyenne");
        FR_NAMES.put("bench press - powerlifting", "Développé couché powerlifting");
        FR_NAMES.put("bench press - with bands", "Développé couché avec élastiques");
        FR_NAMES.put("bench press with chains", "Développé couché avec chaînes");
        FR_NAMES.put("board press", "Board press");
        FR_NAMES.put("bodyweight flyes", "Écarté poids du corps");
        FR_NAMES.put("cable chest press", "Développé couché poulie");
        FR_NAMES.put("cable crossover", "Croisé poulie");
        FR_NAMES.put("cable incline pushdown", "Pushdown poulie inclinée");
        FR_NAMES.put("chain press", "Développé couché avec chaînes");
        FR_NAMES.put("close-grip barbell bench press", "Développé couché prise serrée barre");
        FR_NAMES.put("close-grip dumbbell press", "Développé couché prise serrée haltères");
        FR_NAMES.put("close-grip ez-bar press", "Développé couché prise serrée barre EZ");
        FR_NAMES.put("decline barbell bench press", "Développé décliné barre");
        FR_NAMES.put("decline close-grip bench to skull crusher", "Développé décliné prise serrée vers barre au front");
        FR_NAMES.put("decline dumbbell bench press", "Développé décliné haltères");
        FR_NAMES.put("decline smith press", "Développé décliné Smith");
        FR_NAMES.put("dumbbell bench press", "Développé couché haltères");
        FR_NAMES.put("dumbbell bench press with neutral grip", "Développé couché haltères prise neutre");
        FR_NAMES.put("dumbbell floor press", "Développé sol haltères");
        FR_NAMES.put("flat bench cable flyes", "Écarté poulie sur banc plat");
        FR_NAMES.put("floor press", "Développé sol barre");
        FR_NAMES.put("floor press with chains", "Développé sol avec chaînes");
        FR_NAMES.put("hammer grip incline db bench press", "Développé incliné haltères prise marteau");
        FR_NAMES.put("incline cable chest press", "Développé incliné poulie");
        FR_NAMES.put("incline cable flye", "Écarté incliné poulie");
        FR_NAMES.put("incline dumbbell bench with palms facing in", "Développé incliné haltères paumes face à face");
        FR_NAMES.put("incline dumbbell flyes", "Écarté incliné haltères");
        FR_NAMES.put("incline dumbbell flyes - with a twist", "Écarté incliné haltères avec rotation");
        FR_NAMES.put("incline dumbbell press", "Développé incliné haltères");
        FR_NAMES.put("incline push-up", "Pompes inclinées");
        FR_NAMES.put("incline push-up close-grip", "Pompes inclinées prise serrée");
        FR_NAMES.put("incline push-up depth jump", "Pompes inclinées sautées");
        FR_NAMES.put("incline push-up medium", "Pompes inclinées prise moyenne");
        FR_NAMES.put("incline push-up reverse grip", "Pompes inclinées prise inversée");
        FR_NAMES.put("incline push-up wide", "Pompes inclinées prise large");
        FR_NAMES.put("isometric chest squeezes", "Contraction isométrique pectoraux");
        FR_NAMES.put("leverage chest press", "Développé couché machine levier");
        FR_NAMES.put("leverage decline chest press", "Développé décliné machine levier");
        FR_NAMES.put("leverage incline chest press", "Développé incliné machine levier");
        FR_NAMES.put("low cable crossover", "Croisé poulie basse");
        FR_NAMES.put("machine bench press", "Développé couché machine");
        FR_NAMES.put("neck press", "Développé nuque");
        FR_NAMES.put("one arm dumbbell bench press", "Développé couché haltère unilatéral");
        FR_NAMES.put("one arm floor press", "Développé sol unilatéral");
        FR_NAMES.put("one-arm flat bench dumbbell flye", "Écarté unilatéral sur banc plat");
        FR_NAMES.put("pin presses", "Développé depuis les goupilles");
        FR_NAMES.put("push-ups - close triceps position", "Pompes triceps serrés");
        FR_NAMES.put("push-ups with feet elevated", "Pompes pieds surélevés");
        FR_NAMES.put("push-ups with feet on an exercise ball", "Pompes pieds sur ballon");
        FR_NAMES.put("push-up wide", "Pompes prise large");
        FR_NAMES.put("pushups", "Pompes");
        FR_NAMES.put("pushups (close and wide hand positions)", "Pompes prise serrée et large");
        FR_NAMES.put("reverse band bench press", "Développé couché bande inversée");
        FR_NAMES.put("reverse triceps bench press", "Développé couché prise inversée triceps");
        FR_NAMES.put("single-arm push-up", "Pompes unilatérales");
        FR_NAMES.put("smith machine bench press", "Développé couché Smith");
        FR_NAMES.put("smith machine close-grip bench press", "Développé couché prise serrée Smith");
        FR_NAMES.put("smith machine decline press", "Développé décliné Smith");
        FR_NAMES.put("smith machine incline bench press", "Développé incliné Smith");
        FR_NAMES.put("standing cable chest press", "Développé couché poulie debout");
        FR_NAMES.put("svend press", "Svend press");
        FR_NAMES.put("wide-grip barbell bench press", "Développé couché prise large barre");
        FR_NAMES.put("wide-grip decline barbell bench press", "Développé décliné prise large barre");
        FR_NAMES.put("clock push-up", "Pompes en rotation");
        FR_NAMES.put("alternating floor press", "Développé sol alterné");
        FR_NAMES.put("close-grip push-up off of a dumbbell", "Pompes prise serrée sur haltère");
        FR_NAMES.put("decline push-up", "Pompes déclinées");
        FR_NAMES.put("dips - chest version", "Dips pectoraux");
        FR_NAMES.put("dips - triceps version", "Dips triceps");
        FR_NAMES.put("plyo push-up", "Pompes pliométriques");
        FR_NAMES.put("plyo kettlebell pushups", "Pompes plio sur kettlebell");
        FR_NAMES.put("jm press", "JM press");
        FR_NAMES.put("extended range one-arm kettlebell floor press", "Développé sol kettlebell unilatéral amplitude complète");
        FR_NAMES.put("one-arm kettlebell floor press", "Développé sol kettlebell unilatéral");
        FR_NAMES.put("leg-over floor press", "Développé sol jambe croisée");

        // ── Dos / Rowing ───────────────────────────────────────────────────
        FR_NAMES.put("alternating kettlebell row", "Rowing kettlebell alterné");
        FR_NAMES.put("alternating renegade row", "Rowing renegade alterné");
        FR_NAMES.put("barbell incline shoulder raise", "Élévation épaules incliné barre");
        FR_NAMES.put("barbell rear delt row", "Rowing deltoïde arrière barre");
        FR_NAMES.put("barbell rollout from bench", "Rouleau abdominal barre sur banc");
        FR_NAMES.put("bent over barbell row", "Rowing barre penché");
        FR_NAMES.put("bent over dumbbell rear delt raise with head on bench", "Élévation arrière haltères penché tête sur banc");
        FR_NAMES.put("bent over low-pulley side lateral", "Élévation latérale poulie basse penché");
        FR_NAMES.put("bent over one-arm long bar row", "Rowing unilatéral longue barre penché");
        FR_NAMES.put("bent over two-arm long bar row", "Rowing deux bras longue barre penché");
        FR_NAMES.put("bent over two-dumbbell row", "Rowing deux haltères penché");
        FR_NAMES.put("bent over two-dumbbell row with palms in", "Rowing deux haltères penché paumes face à face");
        FR_NAMES.put("bodyweight mid row", "Rowing horizontal poids du corps");
        FR_NAMES.put("cable rope rear-delt rows", "Rowing corde poulie deltoïde arrière");
        FR_NAMES.put("close-grip front lat pulldown", "Tirage poitrine prise serrée");
        FR_NAMES.put("elevated cable rows", "Rowing poulie surélevé");
        FR_NAMES.put("full range-of-motion lat pulldown", "Tirage poulie amplitude complète");
        FR_NAMES.put("gironda sternum chins", "Traction sternum Gironda");
        FR_NAMES.put("inverted row", "Rowing inversé");
        FR_NAMES.put("inverted row with straps", "Rowing inversé avec sangles");
        FR_NAMES.put("kneeling high pulley row", "Rowing poulie haute à genoux");
        FR_NAMES.put("kneeling single-arm high pulley row", "Rowing poulie haute unilatéral à genoux");
        FR_NAMES.put("leverage high row", "Rowing machine levier");
        FR_NAMES.put("leverage iso row", "Rowing iso machine levier");
        FR_NAMES.put("low pulley row to neck", "Rowing poulie basse vers le cou");
        FR_NAMES.put("lying cambered barbell row", "Rowing barre courbée allongé");
        FR_NAMES.put("lying t-bar row", "Rowing T-bar allongé");
        FR_NAMES.put("one-arm dumbbell row", "Rowing haltère unilatéral");
        FR_NAMES.put("one-arm kettlebell row", "Rowing kettlebell unilatéral");
        FR_NAMES.put("one-arm long bar row", "Rowing longue barre unilatéral");
        FR_NAMES.put("one-arm incline lateral raise", "Élévation latérale inclinée unilatérale");
        FR_NAMES.put("reverse flyes", "Écarté inverse");
        FR_NAMES.put("reverse flyes with external rotation", "Écarté inverse avec rotation externe");
        FR_NAMES.put("reverse grip bent-over rows", "Rowing penché prise inversée");
        FR_NAMES.put("reverse machine flyes", "Écarté inverse machine");
        FR_NAMES.put("rocky pull-ups/pulldowns", "Tractions/tirage Rocky");
        FR_NAMES.put("rope straight-arm pulldown", "Tirage bras tendu à la corde");
        FR_NAMES.put("scapular pull-up", "Traction scapulaire");
        FR_NAMES.put("seated cable rows", "Rowing poulie basse assis");
        FR_NAMES.put("seated cable shoulder press", "Développé épaules poulie assis");
        FR_NAMES.put("seated one-arm cable pulley rows", "Rowing poulie unilatéral assis");
        FR_NAMES.put("shotgun row", "Rowing shotgun");
        FR_NAMES.put("side to side chins", "Tractions latérales");
        FR_NAMES.put("single-arm cable crossover", "Croisé poulie unilatéral");
        FR_NAMES.put("smith machine bent over row", "Rowing penché Smith");
        FR_NAMES.put("smith machine upright row", "Rowing vertical Smith");
        FR_NAMES.put("straight bar bench mid rows", "Rowing barre assis sur banc");
        FR_NAMES.put("straight-arm pulldown", "Tirage bras tendu poulie");
        FR_NAMES.put("suspended row", "Rowing suspendu");
        FR_NAMES.put("t-bar row with handle", "Rowing T-bar avec poignée");
        FR_NAMES.put("two-arm kettlebell row", "Rowing deux kettlebells");
        FR_NAMES.put("underhand cable pulldowns", "Tirage poulie prise sous-main");
        FR_NAMES.put("upright barbell row", "Rowing vertical barre");
        FR_NAMES.put("upright cable row", "Rowing vertical poulie");
        FR_NAMES.put("upright row - with bands", "Rowing vertical avec élastiques");
        FR_NAMES.put("v-bar pulldown", "Tirage poulie barre en V");
        FR_NAMES.put("v-bar pullup", "Traction barre en V");
        FR_NAMES.put("wide-grip lat pulldown", "Tirage poulie prise large");
        FR_NAMES.put("wide-grip pulldown behind the neck", "Tirage nuque prise large");
        FR_NAMES.put("wide-grip rear pull-up", "Traction arrière prise large");
        FR_NAMES.put("dumbbell incline row", "Rowing incliné haltères");
        FR_NAMES.put("back flyes - with bands", "Écarté dos avec élastiques");
        FR_NAMES.put("barbell ab rollout", "Rouleau abdominal barre");
        FR_NAMES.put("barbell ab rollout - on knees", "Rouleau abdominal barre à genoux");
        FR_NAMES.put("mixed grip chin", "Traction prise mixte");
        FR_NAMES.put("one arm chin-up", "Traction unilatérale");
        FR_NAMES.put("one arm lat pulldown", "Tirage poulie unilatéral");
        FR_NAMES.put("pullups", "Tractions");
        FR_NAMES.put("chin-up", "Traction prise supination");
        FR_NAMES.put("weighted pull ups", "Tractions lestées");
        FR_NAMES.put("wide-grip standing barbell curl", "Curl barre prise large debout");

        // ── Épaules ────────────────────────────────────────────────────────
        FR_NAMES.put("alternating cable shoulder press", "Développé épaules poulie alterné");
        FR_NAMES.put("alternating deltoid raise", "Élévation deltoïde alternée");
        FR_NAMES.put("alternating kettlebell press", "Développé kettlebell alterné");
        FR_NAMES.put("anti-gravity press", "Développé anti-gravité");
        FR_NAMES.put("arnold dumbbell press", "Arnold press haltères");
        FR_NAMES.put("barbell shoulder press", "Développé militaire barre");
        FR_NAMES.put("bent over low-pulley side lateral", "Élévation latérale poulie basse penché");
        FR_NAMES.put("cable seated lateral raise", "Élévation latérale assis poulie");
        FR_NAMES.put("cable shoulder press", "Développé épaules poulie");
        FR_NAMES.put("car drivers", "Car drivers");
        FR_NAMES.put("cuban press", "Cuban press");
        FR_NAMES.put("dumbbell one-arm shoulder press", "Développé épaules unilatéral haltères");
        FR_NAMES.put("dumbbell raise", "Élévation haltères");
        FR_NAMES.put("dumbbell scaption", "Scaption haltères");
        FR_NAMES.put("dumbbell shoulder press", "Développé épaules haltères");
        FR_NAMES.put("face pull", "Face pull");
        FR_NAMES.put("front cable raise", "Élévation frontale poulie");
        FR_NAMES.put("front dumbbell raise", "Élévation frontale haltères");
        FR_NAMES.put("front incline dumbbell raise", "Élévation frontale inclinée haltères");
        FR_NAMES.put("front plate raise", "Élévation frontale disque");
        FR_NAMES.put("front raise and pullover", "Élévation frontale et pull-over");
        FR_NAMES.put("front two-dumbbell raise", "Élévation frontale deux haltères");
        FR_NAMES.put("lateral raise - with bands", "Élévation latérale avec élastiques");
        FR_NAMES.put("leverage shoulder press", "Développé épaules machine levier");
        FR_NAMES.put("machine shoulder (military) press", "Développé militaire machine");
        FR_NAMES.put("overhead slam", "Lancer overhead");
        FR_NAMES.put("push press", "Push press");
        FR_NAMES.put("push press - behind the neck", "Push press nuque");
        FR_NAMES.put("reverse band power squat", "Squat puissance bande inversée");
        FR_NAMES.put("reverse barbell preacher curls", "Curl pupitre barre inversé");
        FR_NAMES.put("seated barbell military press", "Développé militaire barre assis");
        FR_NAMES.put("seated bent-over rear delt raise", "Élévation arrière deltoïde assis penché");
        FR_NAMES.put("seated dumbbell press", "Développé épaules haltères assis");
        FR_NAMES.put("shoulder press - with bands", "Développé épaules avec élastiques");
        FR_NAMES.put("side lateral raise", "Élévation latérale");
        FR_NAMES.put("side laterals to front raise", "Élévation latérale vers élévation frontale");
        FR_NAMES.put("smith machine overhead shoulder press", "Développé militaire Smith");
        FR_NAMES.put("smith machine one-arm upright row", "Rowing vertical unilatéral Smith");
        FR_NAMES.put("standing alternating dumbbell press", "Développé haltères alterné debout");
        FR_NAMES.put("standing bradford press", "Bradford press debout");
        FR_NAMES.put("standing cable lift", "Élévation poulie debout");
        FR_NAMES.put("standing cable wood chop", "Bûcheron à la poulie debout");
        FR_NAMES.put("standing dumbbell press", "Développé haltères debout");
        FR_NAMES.put("standing dumbbell upright row", "Rowing vertical haltères debout");
        FR_NAMES.put("standing front barbell raise over head", "Élévation frontale barre au-dessus de la tête");
        FR_NAMES.put("standing low-pulley deltoid raise", "Élévation deltoïde poulie basse debout");
        FR_NAMES.put("standing military press", "Développé militaire debout");
        FR_NAMES.put("standing palm-in one-arm dumbbell press", "Développé unilatéral haltère prise neutre debout");
        FR_NAMES.put("standing palms-in dumbbell press", "Développé haltères prise neutre debout");
        FR_NAMES.put("bradford/rocky presses", "Bradford press");
        FR_NAMES.put("dumbbell incline shoulder raise", "Élévation épaules inclinée haltères");
        FR_NAMES.put("dumbbell one-arm upright row", "Rowing vertical unilatéral haltère");
        FR_NAMES.put("smith machine incline shoulder raise", "Élévation épaules inclinée Smith");
        FR_NAMES.put("see-saw press (alternating side press)", "Presse alternée latérale");
        FR_NAMES.put("around the worlds", "Around the worlds");
        FR_NAMES.put("spell caster", "Spell caster");
        FR_NAMES.put("single dumbbell raise", "Élévation haltère unilatéral");
        FR_NAMES.put("standing dumbbell straight-arm front delt raise above head", "Élévation frontale bras tendus au-dessus de la tête");
        FR_NAMES.put("standing overhead barbell triceps extension", "Extension triceps barre au-dessus de la tête debout");
        FR_NAMES.put("standing one-arm dumbbell triceps extension", "Extension triceps haltère unilatérale debout");
        FR_NAMES.put("dumbbell lying one-arm rear lateral raise", "Élévation arrière unilatérale allongée haltère");
        FR_NAMES.put("dumbbell lying rear lateral raise", "Élévation arrière allongée haltères");
        FR_NAMES.put("lying rear delt raise", "Élévation arrière deltoïde allongé");
        FR_NAMES.put("lying one-arm lateral raise", "Élévation latérale unilatérale allongée");
        FR_NAMES.put("one-arm incline lateral raise", "Élévation latérale inclinée unilatérale");
        FR_NAMES.put("one-arm side laterals", "Élévation latérale unilatérale");
        FR_NAMES.put("seated bent-over one-arm dumbbell triceps extension", "Extension triceps unilatérale assise penchée");
        FR_NAMES.put("seated side lateral raise", "Élévation latérale assise");

        // ── Biceps ─────────────────────────────────────────────────────────
        FR_NAMES.put("alternate hammer curl", "Curl marteau alterné");
        FR_NAMES.put("alternate incline dumbbell curl", "Curl incliné haltères alterné");
        FR_NAMES.put("barbell curl", "Curl barre");
        FR_NAMES.put("barbell curls lying against an incline", "Curl barre allongé incliné");
        FR_NAMES.put("cable hammer curls - rope attachment", "Curl marteau poulie avec corde");
        FR_NAMES.put("cable preacher curl", "Curl pupitre poulie");
        FR_NAMES.put("close-grip ez bar curl", "Curl barre EZ prise serrée");
        FR_NAMES.put("close-grip ez-bar curl with band", "Curl barre EZ prise serrée avec élastique");
        FR_NAMES.put("close-grip standing barbell curl", "Curl barre prise serrée debout");
        FR_NAMES.put("concentration curls", "Curl concentration");
        FR_NAMES.put("cross body hammer curl", "Curl marteau croisé");
        FR_NAMES.put("drag curl", "Drag curl");
        FR_NAMES.put("dumbbell alternate bicep curl", "Curl biceps haltères alterné");
        FR_NAMES.put("dumbbell bicep curl", "Curl biceps haltères");
        FR_NAMES.put("dumbbell lying supination", "Curl supination allongé haltère");
        FR_NAMES.put("dumbbell prone incline curl", "Curl incliné allongé haltères");
        FR_NAMES.put("ez-bar curl", "Curl barre EZ");
        FR_NAMES.put("flexor incline dumbbell curls", "Curl fléchisseur incliné haltères");
        FR_NAMES.put("hammer curls", "Curl marteau");
        FR_NAMES.put("high cable curls", "Curl poulie haute");
        FR_NAMES.put("incline dumbbell curl", "Curl incliné haltères");
        FR_NAMES.put("incline hammer curls", "Curl marteau incliné");
        FR_NAMES.put("incline inner biceps curl", "Curl biceps interne incliné");
        FR_NAMES.put("lying cable curl", "Curl poulie allongé");
        FR_NAMES.put("lying close-grip bar curl on high pulley", "Curl prise serrée poulie haute allongé");
        FR_NAMES.put("lying high bench barbell curl", "Curl barre banc haut allongé");
        FR_NAMES.put("lying supine dumbbell curl", "Curl haltères allongé sur le dos");
        FR_NAMES.put("machine bicep curl", "Curl biceps machine");
        FR_NAMES.put("machine preacher curls", "Curl pupitre machine");
        FR_NAMES.put("one arm dumbbell preacher curl", "Curl pupitre haltère unilatéral");
        FR_NAMES.put("overhead cable curl", "Curl poulie au-dessus de la tête");
        FR_NAMES.put("preacher curl", "Curl pupitre barre");
        FR_NAMES.put("preacher hammer dumbbell curl", "Curl marteau pupitre haltères");
        FR_NAMES.put("reverse barbell curl", "Curl barre inversé");
        FR_NAMES.put("reverse cable curl", "Curl poulie inversé");
        FR_NAMES.put("reverse plate curls", "Curl disque inversé");
        FR_NAMES.put("seated close-grip concentration barbell curl", "Curl concentration prise serrée barre assis");
        FR_NAMES.put("seated dumbbell curl", "Curl haltères assis");
        FR_NAMES.put("seated dumbbell inner biceps curl", "Curl biceps interne haltères assis");
        FR_NAMES.put("spider curl", "Spider curl");
        FR_NAMES.put("standing biceps cable curl", "Curl biceps poulie debout");
        FR_NAMES.put("standing concentration curl", "Curl concentration debout");
        FR_NAMES.put("standing inner-biceps curl", "Curl biceps interne debout");
        FR_NAMES.put("standing one-arm cable curl", "Curl poulie unilatéral debout");
        FR_NAMES.put("standing one-arm dumbbell curl over incline bench", "Curl haltère unilatéral sur banc incliné debout");
        FR_NAMES.put("two-arm dumbbell preacher curl", "Curl pupitre deux haltères");
        FR_NAMES.put("zottman curl", "Curl Zottman");
        FR_NAMES.put("zottman preacher curl", "Curl Zottman pupitre");
        FR_NAMES.put("dumbbell lying pronation", "Pronation haltère allongé");

        // ── Triceps ────────────────────────────────────────────────────────
        FR_NAMES.put("band skull crusher", "Barre au front avec élastique");
        FR_NAMES.put("bench dips", "Dips sur banc");
        FR_NAMES.put("body tricep press", "Développé triceps poids du corps");
        FR_NAMES.put("cable incline triceps extension", "Extension triceps incliné poulie");
        FR_NAMES.put("cable lying triceps extension", "Extension triceps allongé poulie");
        FR_NAMES.put("cable one arm tricep extension", "Extension triceps unilatérale poulie");
        FR_NAMES.put("cable rope overhead triceps extension", "Extension triceps corde au-dessus de la tête");
        FR_NAMES.put("chain handle extension", "Extension triceps avec chaîne");
        FR_NAMES.put("close-grip front lat pulldown", "Tirage poitrine prise serrée");
        FR_NAMES.put("decline dumbbell triceps extension", "Extension triceps haltères décliné");
        FR_NAMES.put("decline ez bar triceps extension", "Extension triceps barre EZ décliné");
        FR_NAMES.put("dip machine", "Machine dips");
        FR_NAMES.put("dumbbell one-arm triceps extension", "Extension triceps unilatérale haltère");
        FR_NAMES.put("dumbbell tricep extension -pronated grip", "Extension triceps haltère prise pronée");
        FR_NAMES.put("ez-bar skullcrusher", "Barre au front barre EZ");
        FR_NAMES.put("incline barbell triceps extension", "Extension triceps barre incliné");
        FR_NAMES.put("kneeling cable triceps extension", "Extension triceps poulie à genoux");
        FR_NAMES.put("low cable triceps extension", "Extension triceps poulie basse");
        FR_NAMES.put("lying close-grip barbell triceps extension behind the head", "Barre au front prise serrée derrière la tête");
        FR_NAMES.put("lying close-grip barbell triceps press to chin", "Développé prise serrée vers le menton allongé");
        FR_NAMES.put("lying dumbbell tricep extension", "Extension triceps haltère allongé");
        FR_NAMES.put("lying triceps press", "Développé triceps allongé");
        FR_NAMES.put("machine triceps extension", "Extension triceps machine");
        FR_NAMES.put("one arm pronated dumbbell triceps extension", "Extension triceps haltère pronée unilatérale");
        FR_NAMES.put("one arm supinated dumbbell triceps extension", "Extension triceps haltère supinée unilatérale");
        FR_NAMES.put("parallel bar dip", "Dips aux barres parallèles");
        FR_NAMES.put("reverse grip triceps pushdown", "Pushdown triceps prise inversée");
        FR_NAMES.put("ring dips", "Dips aux anneaux");
        FR_NAMES.put("seated bent-over two-arm dumbbell triceps extension", "Extension triceps deux haltères assis penché");
        FR_NAMES.put("seated triceps press", "Développé triceps assis");
        FR_NAMES.put("sled overhead triceps extension", "Extension triceps overhead traîneau");
        FR_NAMES.put("speed band overhead triceps", "Extension triceps overhead élastique explosif");
        FR_NAMES.put("standing bent-over one-arm dumbbell triceps extension", "Extension triceps haltère penché unilatéral debout");
        FR_NAMES.put("standing bent-over two-arm dumbbell triceps extension", "Extension triceps deux haltères penché debout");
        FR_NAMES.put("standing low-pulley one-arm triceps extension", "Extension triceps poulie basse unilatérale debout");
        FR_NAMES.put("standing dumbbell triceps extension", "Extension triceps haltère debout");
        FR_NAMES.put("standing towel triceps extension", "Extension triceps avec serviette debout");
        FR_NAMES.put("tate press", "Tate press");
        FR_NAMES.put("tricep dumbbell kickback", "Kickback triceps haltère");
        FR_NAMES.put("triceps overhead extension with rope", "Extension triceps corde overhead");
        FR_NAMES.put("triceps pushdown", "Pushdown triceps");
        FR_NAMES.put("triceps pushdown - rope attachment", "Pushdown triceps avec corde");
        FR_NAMES.put("triceps pushdown - v-bar attachment", "Pushdown triceps avec barre en V");
        FR_NAMES.put("weighted bench dip", "Dips sur banc lesté");
        FR_NAMES.put("bench dips", "Dips sur banc");

        // ── Mollets ────────────────────────────────────────────────────────
        FR_NAMES.put("barbell seated calf raise", "Relevé de mollets assis barre");
        FR_NAMES.put("calf press", "Presse mollets");
        FR_NAMES.put("calf press on the leg press machine", "Presse mollets sur presse à cuisses");
        FR_NAMES.put("calf raise on a dumbbell", "Relevé de mollets sur haltère");
        FR_NAMES.put("calf raises - with bands", "Relevé de mollets avec élastiques");
        FR_NAMES.put("calf-machine shoulder shrug", "Haussement d'épaules machine mollets");
        FR_NAMES.put("dumbbell seated one-leg calf raise", "Relevé de mollets unilatéral assis haltère");
        FR_NAMES.put("rocking standing calf raise", "Relevé de mollets debout oscillant");
        FR_NAMES.put("seated calf raise", "Relevé de mollets assis");
        FR_NAMES.put("smith machine calf raise", "Relevé de mollets Smith");
        FR_NAMES.put("smith machine reverse calf raises", "Relevé de mollets inversé Smith");
        FR_NAMES.put("standing barbell calf raise", "Relevé de mollets barre debout");
        FR_NAMES.put("standing calf raises", "Relevé de mollets debout");
        FR_NAMES.put("standing dumbbell calf raise", "Relevé de mollets haltère debout");
        FR_NAMES.put("donkey calf raises", "Relevé de mollets en âne");

        // ── Dos inférieur / Lombaires ──────────────────────────────────────
        FR_NAMES.put("good morning", "Good morning");
        FR_NAMES.put("good morning off pins", "Good morning depuis les goupilles");
        FR_NAMES.put("band good morning", "Good morning avec élastique");
        FR_NAMES.put("band good morning (pull through)", "Good morning pull-through avec élastique");
        FR_NAMES.put("hanging bar good morning", "Good morning barre suspendu");
        FR_NAMES.put("hyperextensions (back extensions)", "Hyperextensions (extensions de dos)");
        FR_NAMES.put("hyperextensions with no hyperextension bench", "Hyperextensions sans banc");
        FR_NAMES.put("seated good mornings", "Good morning assis");
        FR_NAMES.put("weighted ball hyperextension", "Hyperextension avec ballon lesté");
        FR_NAMES.put("superman", "Superman");

        // ── Tractions & Tirage ─────────────────────────────────────────────
        FR_NAMES.put("band assisted pull-up", "Traction assistée par élastique");
        FR_NAMES.put("kipping muscle up", "Muscle-up kipping");
        FR_NAMES.put("muscle up", "Muscle-up");
        FR_NAMES.put("one handed hang", "Suspension unilatérale");
        FR_NAMES.put("rope climb", "Grimper à la corde");
        FR_NAMES.put("straight raises on incline bench", "Élévation droite sur banc incliné");

        // ── Haussement d'épaules / Trapèzes ───────────────────────────────
        FR_NAMES.put("barbell shrug", "Haussement d'épaules barre");
        FR_NAMES.put("barbell shrug behind the back", "Haussement d'épaules barre derrière le dos");
        FR_NAMES.put("clean shrug", "Haussement d'épaules épaulé");
        FR_NAMES.put("dumbbell shrug", "Haussement d'épaules haltères");
        FR_NAMES.put("leverage shrug", "Haussement d'épaules machine levier");
        FR_NAMES.put("middle back shrug", "Haussement milieu du dos");
        FR_NAMES.put("snatch shrug", "Haussement d'épaules arraché");
        FR_NAMES.put("smith machine behind the back shrug", "Haussement d'épaules derrière le dos Smith");
        FR_NAMES.put("cable shrugs", "Haussement d'épaules poulie");
        FR_NAMES.put("cleans - with shrug", "Épaulé avec haussement d'épaules");

        // ── Avant-bras / Poignet ───────────────────────────────────────────
        FR_NAMES.put("cable wrist curl", "Curl poignet poulie");
        FR_NAMES.put("finger curls", "Curl des doigts");
        FR_NAMES.put("palms-down dumbbell wrist curl over a bench", "Curl poignet haltère prise pronée sur banc");
        FR_NAMES.put("palms-down wrist curl over a bench", "Curl poignet prise pronée sur banc");
        FR_NAMES.put("palms-up barbell wrist curl over a bench", "Curl poignet barre prise supinée sur banc");
        FR_NAMES.put("palms-up dumbbell wrist curl over a bench", "Curl poignet haltère prise supinée sur banc");
        FR_NAMES.put("plate pinch", "Pincement de disque");
        FR_NAMES.put("seated dumbbell palms-down wrist curl", "Curl poignet haltère prise pronée assis");
        FR_NAMES.put("seated dumbbell palms-up wrist curl", "Curl poignet haltère prise supinée assis");
        FR_NAMES.put("seated one-arm dumbbell palms-down wrist curl", "Curl poignet haltère prise pronée unilatéral assis");
        FR_NAMES.put("seated one-arm dumbbell palms-up wrist curl", "Curl poignet haltère prise supinée unilatéral assis");
        FR_NAMES.put("seated palm-up barbell wrist curl", "Curl poignet barre prise supinée assis");
        FR_NAMES.put("seated palms-down barbell wrist curl", "Curl poignet barre prise pronée assis");
        FR_NAMES.put("seated two-arm palms-up low-pulley wrist curl", "Curl poignet deux bras poulie basse assis");
        FR_NAMES.put("standing palms-up barbell behind the back wrist curl", "Curl poignet barre derrière le dos prise supinée");
        FR_NAMES.put("wrist roller", "Rouleau poignet");

        // ── Haltérophilie / Olympic lifting ───────────────────────────────
        FR_NAMES.put("clean", "Épaulé");
        FR_NAMES.put("clean and jerk", "Épaulé-jeté");
        FR_NAMES.put("clean and press", "Épaulé-développé");
        FR_NAMES.put("clean deadlift", "Soulevé de terre épaulé");
        FR_NAMES.put("clean from blocks", "Épaulé depuis les blocs");
        FR_NAMES.put("clean pull", "Tiré d'épaulé");
        FR_NAMES.put("hang clean", "Épaulé suspendu");
        FR_NAMES.put("hang clean - below the knees", "Épaulé suspendu sous les genoux");
        FR_NAMES.put("hang snatch", "Arraché suspendu");
        FR_NAMES.put("hang snatch - below knees", "Arraché suspendu sous les genoux");
        FR_NAMES.put("heaving snatch balance", "Équilibre arraché");
        FR_NAMES.put("jerk balance", "Balance jeté");
        FR_NAMES.put("muscle snatch", "Arraché musculaire");
        FR_NAMES.put("power clean", "Épaulé de force");
        FR_NAMES.put("power clean from blocks", "Épaulé de force depuis les blocs");
        FR_NAMES.put("power jerk", "Jeté de force");
        FR_NAMES.put("power snatch", "Arraché de force");
        FR_NAMES.put("power snatch from blocks", "Arraché de force depuis les blocs");
        FR_NAMES.put("snatch", "Arraché");
        FR_NAMES.put("snatch balance", "Balance arraché");
        FR_NAMES.put("snatch from blocks", "Arraché depuis les blocs");
        FR_NAMES.put("snatch pull", "Tiré d'arraché");
        FR_NAMES.put("split clean", "Épaulé fendu");
        FR_NAMES.put("split jerk", "Jeté fendu");
        FR_NAMES.put("split snatch", "Arraché fendu");
        FR_NAMES.put("squat jerk", "Squat jeté");
        FR_NAMES.put("alternating hang clean", "Épaulé suspendu alterné");
        FR_NAMES.put("bottoms-up clean from the hang position", "Épaulé inversé depuis la position suspendue");
        FR_NAMES.put("double kettlebell alternating hang clean", "Épaulé suspendu alterné deux kettlebells");
        FR_NAMES.put("double kettlebell jerk", "Jeté deux kettlebells");
        FR_NAMES.put("double kettlebell push press", "Push press deux kettlebells");
        FR_NAMES.put("double kettlebell snatch", "Arraché deux kettlebells");
        FR_NAMES.put("one-arm kettlebell clean", "Épaulé kettlebell unilatéral");
        FR_NAMES.put("one-arm kettlebell clean and jerk", "Épaulé-jeté kettlebell unilatéral");
        FR_NAMES.put("one-arm kettlebell jerk", "Jeté kettlebell unilatéral");
        FR_NAMES.put("one-arm kettlebell military press to the side", "Développé militaire kettlebell latéral unilatéral");
        FR_NAMES.put("one-arm kettlebell para press", "Para press kettlebell unilatéral");
        FR_NAMES.put("one-arm kettlebell push press", "Push press kettlebell unilatéral");
        FR_NAMES.put("one-arm kettlebell snatch", "Arraché kettlebell unilatéral");
        FR_NAMES.put("one-arm kettlebell split jerk", "Jeté fendu kettlebell unilatéral");
        FR_NAMES.put("one-arm kettlebell split snatch", "Arraché fendu kettlebell unilatéral");
        FR_NAMES.put("two-arm kettlebell clean", "Épaulé deux kettlebells");
        FR_NAMES.put("two-arm kettlebell jerk", "Jeté deux kettlebells");
        FR_NAMES.put("two-arm kettlebell military press", "Développé militaire deux kettlebells");

        // ── Kettlebell ─────────────────────────────────────────────────────
        FR_NAMES.put("advanced kettlebell windmill", "Windmill kettlebell avancé");
        FR_NAMES.put("double kettlebell windmill", "Windmill deux kettlebells");
        FR_NAMES.put("kettlebell arnold press", "Arnold press kettlebell");
        FR_NAMES.put("kettlebell dead clean", "Épaulé mort kettlebell");
        FR_NAMES.put("kettlebell figure 8", "Figure 8 kettlebell");
        FR_NAMES.put("kettlebell hang clean", "Épaulé suspendu kettlebell");
        FR_NAMES.put("kettlebell one-legged deadlift", "Soulevé de terre unilatéral kettlebell");
        FR_NAMES.put("kettlebell pass between the legs", "Passage de kettlebell entre les jambes");
        FR_NAMES.put("kettlebell pirate ships", "Bateaux pirates kettlebell");
        FR_NAMES.put("kettlebell seated press", "Développé assis kettlebell");
        FR_NAMES.put("kettlebell seesaw press", "Presse balancier kettlebell");
        FR_NAMES.put("kettlebell sumo high pull", "High pull sumo kettlebell");
        FR_NAMES.put("kettlebell thruster", "Thruster kettlebell");
        FR_NAMES.put("kettlebell turkish get-up (lunge style)", "Lever turc kettlebell (style fente)");
        FR_NAMES.put("kettlebell turkish get-up (squat style)", "Lever turc kettlebell (style squat)");
        FR_NAMES.put("kettlebell windmill", "Windmill kettlebell");
        FR_NAMES.put("one-arm kettlebell floor press", "Développé sol kettlebell unilatéral");
        FR_NAMES.put("one-arm kettlebell row", "Rowing kettlebell unilatéral");
        FR_NAMES.put("one-arm kettlebell swings", "Swing kettlebell unilatéral");
        FR_NAMES.put("one-arm open palm kettlebell clean", "Épaulé paume ouverte kettlebell unilatéral");
        FR_NAMES.put("open palm kettlebell clean", "Épaulé paume ouverte kettlebell");

        // ── Mobilité / Étirements ──────────────────────────────────────────
        FR_NAMES.put("adductor", "Étirement adducteur");
        FR_NAMES.put("adductor/groin", "Étirement adducteur/aine");
        FR_NAMES.put("all fours quad stretch", "Étirement quadriceps à quatre pattes");
        FR_NAMES.put("ankle circles", "Cercles de cheville");
        FR_NAMES.put("ankle on the knee", "Cheville sur le genou");
        FR_NAMES.put("arm circles", "Cercles de bras");
        FR_NAMES.put("behind head chest stretch", "Étirement pectoraux derrière la tête");
        FR_NAMES.put("butterfly", "Papillon (étirement)");
        FR_NAMES.put("calf stretch elbows against wall", "Étirement mollets coudes au mur");
        FR_NAMES.put("calf stretch hands against wall", "Étirement mollets mains au mur");
        FR_NAMES.put("cat stretch", "Étirement du chat");
        FR_NAMES.put("chair leg extended stretch", "Étirement jambe tendue sur chaise");
        FR_NAMES.put("chair lower back stretch", "Étirement bas du dos sur chaise");
        FR_NAMES.put("chair upper body stretch", "Étirement haut du corps sur chaise");
        FR_NAMES.put("chest and front of shoulder stretch", "Étirement pectoraux et avant des épaules");
        FR_NAMES.put("chest stretch on stability ball", "Étirement pectoraux sur ballon");
        FR_NAMES.put("child's pose", "Posture de l'enfant");
        FR_NAMES.put("chin to chest stretch", "Étirement menton vers la poitrine");
        FR_NAMES.put("dancer's stretch", "Étirement du danseur");
        FR_NAMES.put("dynamic back stretch", "Étirement dynamique du dos");
        FR_NAMES.put("dynamic chest stretch", "Étirement dynamique des pectoraux");
        FR_NAMES.put("elbow circles", "Cercles de coude");
        FR_NAMES.put("groin and back stretch", "Étirement aine et dos");
        FR_NAMES.put("groiners", "Étirement aine (groiners)");
        FR_NAMES.put("hamstring stretch", "Étirement ischio-jambiers");
        FR_NAMES.put("hip circles (prone)", "Cercles de hanche (allongé)");
        FR_NAMES.put("hip flexion with band", "Flexion de hanche avec élastique");
        FR_NAMES.put("hug a ball", "Étirement bras autour d'un ballon");
        FR_NAMES.put("hug knees to chest", "Genoux ramenés à la poitrine");
        FR_NAMES.put("inchworm", "Inchworm");
        FR_NAMES.put("intermediate groin stretch", "Étirement aine intermédiaire");
        FR_NAMES.put("intermediate hip flexor and quad stretch", "Étirement fléchisseur de hanche et quadriceps intermédiaire");
        FR_NAMES.put("iron crosses (stretch)", "Croix de fer (étirement)");
        FR_NAMES.put("it band and glute stretch", "Étirement TFL et fessiers");
        FR_NAMES.put("knee across the body", "Genou croisé devant le corps");
        FR_NAMES.put("knee circles", "Cercles de genou");
        FR_NAMES.put("kneeling forearm stretch", "Étirement avant-bras à genoux");
        FR_NAMES.put("kneeling hip flexor", "Étirement fléchisseur de hanche à genoux");
        FR_NAMES.put("lateral bound", "Bond latéral");
        FR_NAMES.put("leg-up hamstring stretch", "Étirement ischio jambe levée");
        FR_NAMES.put("lying bent leg groin", "Étirement aine jambe fléchie allongé");
        FR_NAMES.put("lying glute", "Étirement fessier allongé");
        FR_NAMES.put("lying hamstring", "Étirement ischio allongé");
        FR_NAMES.put("lying prone quadriceps", "Étirement quadriceps allongé sur le ventre");
        FR_NAMES.put("middle back stretch", "Étirement milieu du dos");
        FR_NAMES.put("neck-smr", "Auto-massage du cou");
        FR_NAMES.put("on your side quad stretch", "Étirement quadriceps sur le côté");
        FR_NAMES.put("on-your-back quad stretch", "Étirement quadriceps sur le dos");
        FR_NAMES.put("one arm against wall", "Étirement pectoraux bras contre le mur");
        FR_NAMES.put("one half locust", "Demi-sauterelle");
        FR_NAMES.put("one knee to chest", "Genou ramené à la poitrine");
        FR_NAMES.put("overhead lat", "Étirement grand dorsal au-dessus de la tête");
        FR_NAMES.put("overhead stretch", "Étirement au-dessus de la tête");
        FR_NAMES.put("overhead triceps", "Étirement triceps au-dessus de la tête");
        FR_NAMES.put("peroneals stretch", "Étirement péroniers");
        FR_NAMES.put("posterior tibialis stretch", "Étirement tibial postérieur");
        FR_NAMES.put("quad stretch", "Étirement quadriceps");
        FR_NAMES.put("round the world shoulder stretch", "Étirement épaules circulaire");
        FR_NAMES.put("runner's stretch", "Étirement du coureur");
        FR_NAMES.put("seated biceps", "Étirement biceps assis");
        FR_NAMES.put("seated calf stretch", "Étirement mollets assis");
        FR_NAMES.put("seated floor hamstring stretch", "Étirement ischio au sol assis");
        FR_NAMES.put("seated front deltoid", "Étirement deltoïde avant assis");
        FR_NAMES.put("seated glute", "Étirement fessier assis");
        FR_NAMES.put("seated hamstring", "Étirement ischio assis");
        FR_NAMES.put("seated hamstring and calf stretch", "Étirement ischio et mollets assis");
        FR_NAMES.put("seated overhead stretch", "Étirement au-dessus de la tête assis");
        FR_NAMES.put("shoulder circles", "Cercles d'épaules");
        FR_NAMES.put("shoulder stretch", "Étirement des épaules");
        FR_NAMES.put("side lying groin stretch", "Étirement aine latéral allongé");
        FR_NAMES.put("side neck stretch", "Étirement latéral du cou");
        FR_NAMES.put("side wrist pull", "Traction latérale du poignet");
        FR_NAMES.put("side-lying floor stretch", "Étirement au sol sur le côté");
        FR_NAMES.put("spinal stretch", "Étirement spinal");
        FR_NAMES.put("standing biceps stretch", "Étirement biceps debout");
        FR_NAMES.put("standing calf stretch... (elbows against wall)", "Étirement mollets coudes au mur debout");
        FR_NAMES.put("standing elevated quad stretch", "Étirement quadriceps surélevé debout");
        FR_NAMES.put("standing gastrocnemius calf stretch", "Étirement gastrocnémiens debout");
        FR_NAMES.put("standing hamstring and calf stretch", "Étirement ischio et mollets debout");
        FR_NAMES.put("standing hip circles", "Cercles de hanches debout");
        FR_NAMES.put("standing hip flexors", "Étirement fléchisseurs de hanche debout");
        FR_NAMES.put("standing lateral stretch", "Étirement latéral debout");
        FR_NAMES.put("standing pelvic tilt", "Bascule pelvienne debout");
        FR_NAMES.put("standing soleus and achilles stretch", "Étirement soléaire et achille debout");
        FR_NAMES.put("standing toe touches", "Toucher des orteils debout");
        FR_NAMES.put("the straddle", "Grand écart");
        FR_NAMES.put("tricep side stretch", "Étirement latéral triceps");
        FR_NAMES.put("triceps stretch", "Étirement triceps");
        FR_NAMES.put("upper back stretch", "Étirement haut du dos");
        FR_NAMES.put("upper back-leg grab", "Étirement haut du dos avec prise de jambe");
        FR_NAMES.put("upward stretch", "Étirement vers le haut");
        FR_NAMES.put("world's greatest stretch", "Le plus grand étirement du monde");
        FR_NAMES.put("wrist circles", "Cercles de poignet");
        FR_NAMES.put("wrist rotations with straight bar", "Rotations de poignet avec barre droite");
        FR_NAMES.put("downward facing balance", "Équilibre face vers le bas");

        // ── Foam Roll / SMR ────────────────────────────────────────────────
        FR_NAMES.put("anterior tibialis-smr", "Auto-massage tibial antérieur");
        FR_NAMES.put("brachialis-smr", "Auto-massage brachial");
        FR_NAMES.put("calves-smr", "Auto-massage mollets");
        FR_NAMES.put("foot-smr", "Auto-massage des pieds");
        FR_NAMES.put("hamstring-smr", "Auto-massage ischio-jambiers");
        FR_NAMES.put("iliotibial tract-smr", "Auto-massage bandelette ilio-tibiale");
        FR_NAMES.put("latissimus dorsi-smr", "Auto-massage grand dorsal");
        FR_NAMES.put("lower back-smr", "Auto-massage bas du dos");
        FR_NAMES.put("peroneals-smr", "Auto-massage péroniers");
        FR_NAMES.put("piriformis-smr", "Auto-massage piriforme");
        FR_NAMES.put("quadriceps-smr", "Auto-massage quadriceps");
        FR_NAMES.put("rhomboids-smr", "Auto-massage rhomboïdes");

        // ── Cardio / Plyométrie ────────────────────────────────────────────
        FR_NAMES.put("air bike", "Vélo à air");
        FR_NAMES.put("battling ropes", "Cordes ondulatoires");
        FR_NAMES.put("bench jump", "Saut sur banc");
        FR_NAMES.put("bench sprint", "Sprint sur banc");
        FR_NAMES.put("bicycling", "Vélo");
        FR_NAMES.put("bicycling, stationary", "Vélo stationnaire");
        FR_NAMES.put("box jump (multiple response)", "Saut sur boîte (multiple)");
        FR_NAMES.put("box skip", "Saut par-dessus la boîte");
        FR_NAMES.put("carioca quick step", "Pas carioca rapide");
        FR_NAMES.put("depth jump leap", "Saut en profondeur");
        FR_NAMES.put("double leg butt kick", "Coup de talon aux fesses deux jambes");
        FR_NAMES.put("drop push", "Drop push");
        FR_NAMES.put("elliptical trainer", "Elliptique");
        FR_NAMES.put("fast skipping", "Corde à sauter rapide");
        FR_NAMES.put("front box jump", "Saut avant sur boîte");
        FR_NAMES.put("front cone hops (or hurdle hops)", "Sauts par-dessus cônes frontaux");
        FR_NAMES.put("frog hops", "Sauts de grenouille");
        FR_NAMES.put("hurdle hops", "Sauts de haies");
        FR_NAMES.put("jogging, treadmill", "Jogging sur tapis");
        FR_NAMES.put("jump rope", "Corde à sauter");
        FR_NAMES.put("knee tuck jump", "Saut genoux à la poitrine");
        FR_NAMES.put("kneeling arm drill", "Exercice de bras à genoux");
        FR_NAMES.put("lateral box jump", "Saut latéral sur boîte");
        FR_NAMES.put("lateral cone hops", "Sauts latéraux par-dessus cônes");
        FR_NAMES.put("linear 3-part start technique", "Technique de départ linéaire en 3 temps");
        FR_NAMES.put("linear acceleration wall drill", "Exercice d'accélération au mur");
        FR_NAMES.put("linear depth jump", "Saut en profondeur linéaire");
        FR_NAMES.put("mountain climbers", "Grimpeurs de montagne");
        FR_NAMES.put("quick leap", "Bond rapide");
        FR_NAMES.put("recumbent bike", "Vélo couché");
        FR_NAMES.put("rocket jump", "Saut fusée");
        FR_NAMES.put("rope jumping", "Saut à la corde");
        FR_NAMES.put("rowing, stationary", "Rameur");
        FR_NAMES.put("running, treadmill", "Course sur tapis");
        FR_NAMES.put("scissors jump", "Saut de ciseaux");
        FR_NAMES.put("single leg butt kick", "Coup de talon aux fesses unilatéral");
        FR_NAMES.put("single leg push-off", "Poussée unilatérale");
        FR_NAMES.put("single-cone sprint drill", "Sprint d'entraînement cône unique");
        FR_NAMES.put("single-leg hop progression", "Progression de saut unilatéral");
        FR_NAMES.put("single-leg lateral hop", "Saut latéral unilatéral");
        FR_NAMES.put("single-leg stride jump", "Saut d'enjambée unilatéral");
        FR_NAMES.put("skating", "Patinage");
        FR_NAMES.put("side hop-sprint", "Sprint-saut latéral");
        FR_NAMES.put("side standing long jump", "Saut en longueur latéral");
        FR_NAMES.put("spider crawl", "Ramper araignée");
        FR_NAMES.put("split jump", "Saut fendu");
        FR_NAMES.put("stairmaster", "Stepper");
        FR_NAMES.put("star jump", "Jumping jack");
        FR_NAMES.put("step mill", "Escalier mécanique");
        FR_NAMES.put("stride jump crossover", "Saut croisé d'enjambée");
        FR_NAMES.put("trail running/walking", "Course en sentier");
        FR_NAMES.put("walking, treadmill", "Marche sur tapis");
        FR_NAMES.put("wind sprints", "Sprints de vitesse");

        // ── Strongman ─────────────────────────────────────────────────────
        FR_NAMES.put("atlas stone trainer", "Entraîneur atlas stone");
        FR_NAMES.put("atlas stones", "Atlas stones");
        FR_NAMES.put("backward drag", "Traîne arrière");
        FR_NAMES.put("backward medicine ball throw", "Lancer de ballon médicinal vers l'arrière");
        FR_NAMES.put("bear crawl sled drags", "Traîne de traîneau en rampant");
        FR_NAMES.put("car drivers", "Car drivers");
        FR_NAMES.put("conan's wheel", "Roue de Conan");
        FR_NAMES.put("circus bell", "Cloche de cirque");
        FR_NAMES.put("farmer's walk", "Marche du fermier");
        FR_NAMES.put("forward drag with press", "Traîne avant avec développé");
        FR_NAMES.put("keg load", "Chargement de tonneau");
        FR_NAMES.put("log lift", "Lever de tronc");
        FR_NAMES.put("medicine ball chest pass", "Passe de poitrine ballon médicinal");
        FR_NAMES.put("medicine ball full twist", "Torsion complète ballon médicinal");
        FR_NAMES.put("medicine ball scoop throw", "Lancer en cuillère ballon médicinal");
        FR_NAMES.put("one-arm medicine ball slam", "Slam ballon médicinal unilatéral");
        FR_NAMES.put("overhead slam", "Slam au-dessus de la tête");
        FR_NAMES.put("power partials", "Partiels de puissance");
        FR_NAMES.put("power stairs", "Escaliers de puissance");
        FR_NAMES.put("prowler sprint", "Sprint avec traîneau prowler");
        FR_NAMES.put("rack delivery", "Livraison depuis le rack");
        FR_NAMES.put("return push from stance", "Poussée de retour depuis la posture");
        FR_NAMES.put("rickshaw carry", "Portée rickshaw");
        FR_NAMES.put("sandbag load", "Chargement de sac de sable");
        FR_NAMES.put("sledgehammer swings", "Balancement de masse");
        FR_NAMES.put("supine chest throw", "Lancer de poitrine allongé sur le dos");
        FR_NAMES.put("supine one-arm overhead throw", "Lancer overhead unilatéral sur le dos");
        FR_NAMES.put("supine two-arm overhead throw", "Lancer overhead deux bras sur le dos");
        FR_NAMES.put("tire flip", "Retournement de pneu");
        FR_NAMES.put("yoke walk", "Marche au joug");

        // ── Exercices spéciaux ─────────────────────────────────────────────
        FR_NAMES.put("balance board", "Plateau d'équilibre");
        FR_NAMES.put("barbell side bend", "Inclinaison latérale barre");
        FR_NAMES.put("bent press", "Développé penché");
        FR_NAMES.put("board press", "Board press");
        FR_NAMES.put("bottoms up", "Kettlebell à l'envers");
        FR_NAMES.put("bradford/rocky presses", "Bradford/Rocky press");
        FR_NAMES.put("crucifixo", "Crucifixion");
        FR_NAMES.put("downward facing balance", "Équilibre face vers le bas");
        FR_NAMES.put("dumbbell clean", "Épaulé haltères");
        FR_NAMES.put("dumbbell seated box jump", "Saut sur boîte assis avec haltères");
        FR_NAMES.put("external rotation", "Rotation externe");
        FR_NAMES.put("external rotation with band", "Rotation externe avec élastique");
        FR_NAMES.put("external rotation with cable", "Rotation externe poulie");
        FR_NAMES.put("front leg raises", "Relevé de jambe avant");
        FR_NAMES.put("glute kickback", "Kickback fessier");
        FR_NAMES.put("hip lift with band", "Relevé de hanche avec élastique");
        FR_NAMES.put("internal rotation with band", "Rotation interne avec élastique");
        FR_NAMES.put("iron cross", "Croix de fer");
        FR_NAMES.put("isometric neck exercise - front and back", "Exercice isométrique nuque avant-arrière");
        FR_NAMES.put("isometric neck exercise - sides", "Exercice isométrique nuque latéral");
        FR_NAMES.put("isometric wipers", "Essuie-glaces isométriques");
        FR_NAMES.put("leg lift", "Relevé de jambe");
        FR_NAMES.put("london bridges", "London bridges");
        FR_NAMES.put("looking at ceiling", "Regard vers le plafond");
        FR_NAMES.put("lunge pass through", "Fente avec passage");
        FR_NAMES.put("lunge sprint", "Fente sprint");
        FR_NAMES.put("monster walk", "Marche du monstre");
        FR_NAMES.put("moving claw series", "Série griffes en mouvement");
        FR_NAMES.put("muscle snatch", "Arraché musculaire");
        FR_NAMES.put("natural glute ham raise", "Curl ischio naturel");
        FR_NAMES.put("one half locust", "Demi-sauterelle");
        FR_NAMES.put("plate twist", "Rotation de disque");
        FR_NAMES.put("power partials", "Partiels de puissance");
        FR_NAMES.put("pull up to side plank", "Traction vers gainage latéral");
        FR_NAMES.put("push up to side plank", "Pompes vers gainage latéral");
        FR_NAMES.put("pyramid", "Pyramide");
        FR_NAMES.put("rear leg raises", "Relevé de jambe arrière");
        FR_NAMES.put("scott press", "Scott press");
        FR_NAMES.put("side leg raises", "Relevé de jambe latéral");
        FR_NAMES.put("single-arm linear jammer", "Jammer linéaire unilatéral");
        FR_NAMES.put("sled drag - harness", "Traîne de traîneau avec harnais");
        FR_NAMES.put("sled overhead backward walk", "Marche arrière overhead avec traîneau");
        FR_NAMES.put("sled push", "Poussée de traîneau");
        FR_NAMES.put("sled reverse flye", "Écarté inverse avec traîneau");
        FR_NAMES.put("sled row", "Rowing avec traîneau");
        FR_NAMES.put("smith machine hip raise", "Relevé de hanches Smith");
        FR_NAMES.put("speed band overhead triceps", "Extension triceps overhead bande explosive");
        FR_NAMES.put("squat with plate movers", "Squat avec disques mobiles");
        FR_NAMES.put("straight-arm dumbbell pullover", "Pull-over bras tendus haltère");
        FR_NAMES.put("suspended fallout", "Fallout suspendu");
        FR_NAMES.put("suspended push-up", "Pompes suspendues");
        FR_NAMES.put("thigh abductor", "Abducteur de cuisse machine");
        FR_NAMES.put("thigh adductor", "Adducteur de cuisse machine");
        FR_NAMES.put("torso rotation", "Rotation du torse");
        FR_NAMES.put("vertical swing", "Swing vertical");
        FR_NAMES.put("weighted ball side bend", "Inclinaison latérale avec ballon lesté");
        FR_NAMES.put("weighted sissy squat", "Sissy squat lesté");
        FR_NAMES.put("windmills", "Windmills");
        FR_NAMES.put("band hip adductions", "Adductions de hanche avec élastique");
        FR_NAMES.put("band pull apart", "Écartement élastique");
        FR_NAMES.put("catch and overhead throw", "Attraper et lancer overhead");
        FR_NAMES.put("chest push (multiple response)", "Poussée de poitrine (multiple)");
        FR_NAMES.put("chest push (single response)", "Poussée de poitrine (simple)");
        FR_NAMES.put("chest push from 3 point stance", "Poussée de poitrine depuis posture 3 points");
        FR_NAMES.put("chest push with run release", "Poussée de poitrine avec élan");
        FR_NAMES.put("dumbbell lying pronation", "Pronation haltère allongé");
        FR_NAMES.put("dumbbell lying supination", "Supination haltère allongé");
        FR_NAMES.put("elbows back", "Coudes en arrière");
        FR_NAMES.put("hip circles (prone)", "Cercles de hanches allongé");
        FR_NAMES.put("lateral linear jammer", "Jammer linéaire latéral");
        FR_NAMES.put("linear jammer", "Jammer linéaire");
        FR_NAMES.put("overhead cable curl", "Curl poulie au-dessus de la tête");
        FR_NAMES.put("seated head harness neck resistance", "Résistance nuque harnais assis");
        FR_NAMES.put("lying face down plate neck resistance", "Résistance nuque disque allongé face contre sol");
        FR_NAMES.put("lying face up plate neck resistance", "Résistance nuque disque allongé sur le dos");

        // ── Exercices non couverts — laisser en anglais (nomFr = null) ────
        // (ab wheel rollout, etc. — déjà couverts implicitement par nom EN)
        FR_NAMES.put("ab roller", "Roue abdominale");
        FR_NAMES.put("barbell rollout from bench", "Rouleau barre depuis le banc");

        // Normalisation des cas mixtes
        FR_NAMES.put("dumbbell alternate bicep curl", "Curl biceps haltères alterné");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Import principal
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public int importFromFile(Path jsonFile, Path imageSourceDir) throws IOException {
        JsonNode root = objectMapper.readTree(jsonFile.toFile());
        if (!root.isArray()) throw new IllegalArgumentException("Le JSON doit être un tableau d'exercices");

        Path exercicesDir = Paths.get(uploadsDir).resolve("exercices");
        Files.createDirectories(exercicesDir);

        int count = 0;
        for (JsonNode node : root) {
            try {
                count += processExercise(node, exercicesDir, imageSourceDir) ? 1 : 0;
            } catch (Exception e) {
                log.warn("Import ignoré pour '{}': {}", node.path("name").asText(), e.getMessage());
            }
        }
        log.info("Import terminé : {} exercices importés/mis à jour sur {}", count, root.size());
        return count;
    }

    private boolean processExercise(JsonNode node, Path exercicesDir, Path imageSourceDir) throws IOException {
        String externalId = node.path("id").asText(null);
        if (externalId == null || externalId.isBlank()) return false;
        if (repository.findByExternalId(externalId).isPresent()) return false;

        String nameEn = node.path("name").asText(null);
        if (nameEn == null || nameEn.isBlank()) return false;

        String nomFr = FR_NAMES.get(nameEn.toLowerCase().trim());

        // Muscle principal — premier élément de primaryMuscles
        MuscleGroup musclePrincipal = null;
        JsonNode primaryMuscles = node.path("primaryMuscles");
        if (primaryMuscles.isArray() && primaryMuscles.size() > 0) {
            musclePrincipal = MUSCLE_MAP.get(primaryMuscles.get(0).asText("").toLowerCase());
        }

        // Muscles secondaires
        List<MuscleGroup> musclesSecondaires = new ArrayList<>();
        for (JsonNode m : node.path("secondaryMuscles")) {
            MuscleGroup mg = MUSCLE_MAP.get(m.asText("").toLowerCase());
            if (mg != null && mg != musclePrincipal) musclesSecondaires.add(mg);
        }

        TypeEquipement equipement = EQUIPMENT_MAP.getOrDefault(
                node.path("equipment").asText("").toLowerCase(), TypeEquipement.AUTRE);

        NiveauDifficulte difficulte = LEVEL_MAP.getOrDefault(
                node.path("level").asText("").toLowerCase(), NiveauDifficulte.INTERMEDIAIRE);

        // Description EN = instructions concaténées
        StringBuilder desc = new StringBuilder();
        for (JsonNode instr : node.path("instructions")) {
            if (!desc.isEmpty()) desc.append("\n");
            desc.append(instr.asText());
        }

        // Copie des images depuis le dataset source
        String imageFolderName = copyImages(externalId, node.path("images"), exercicesDir, imageSourceDir);

        ExerciceDefinition def = ExerciceDefinition.builder()
                .externalId(externalId)
                .nomEn(nameEn)
                .nomFr(nomFr)
                .descriptionEn(desc.isEmpty() ? null : desc.toString())
                .gifPath(imageFolderName)
                .musclePrincipal(musclePrincipal)
                .musclesSecondaires(musclesSecondaires)
                .typeEquipement(equipement)
                .difficulte(difficulte)
                .build();

        repository.save(def);
        return true;
    }

    /**
     * Copie les images depuis imageSourceDir vers exercicesDir/{externalId}/.
     * Retourne le nom du dossier (= externalId) si au moins une image a été copiée, sinon null.
     */
    private String copyImages(String externalId, JsonNode imagesNode, Path exercicesDir, Path imageSourceDir) throws IOException {
        if (!imagesNode.isArray() || imagesNode.size() == 0) return null;
        if (imageSourceDir == null) return null;

        Path destFolder = exercicesDir.resolve(externalId);
        Files.createDirectories(destFolder);

        boolean anycopied = false;
        for (int i = 0; i < Math.min(imagesNode.size(), 2); i++) {
            String relPath = imagesNode.get(i).asText();
            Path src = imageSourceDir.resolve(relPath);
            if (Files.exists(src)) {
                Files.copy(src, destFolder.resolve(i + ".jpg"), StandardCopyOption.REPLACE_EXISTING);
                anycopied = true;
            } else {
                log.debug("Image non trouvée : {}", src);
            }
        }
        return anycopied ? externalId : null;
    }
}
