package com.kronos.chiron.seance.model;

import com.kronos.chiron.exercice.model.ExerciceDefinition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing an exercise performed during a workout session.
 * It contains details such as the exercise name, comments, timing, and its associated sets (series).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exercice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The name of the exercise.
     */
    private String nom;

    /**
     * Optional comments or notes related to this exercise.
     */
    private String commentaire;

    /**
     * Indique que l'exercice a été réalisé en unilatéral (un membre à la fois, reps saisies
     * par bras/jambe). Sert au calcul du tonnage (facteur ×2 : les deux côtés sont travaillés)
     * et informe Chiron (IA) lors de l'analyse des séances.
     */
    @Column(name = "unilateral", nullable = false)
    @Builder.Default
    private boolean unilateral = false;

    /**
     * Position of this exercise within its parent {@link Seance}, lower = displayed first.
     * Assigned by {@code ProgrammeService.sauvegarderProgramme} from the order of the input DTO,
     * and used as the JPA {@code @OrderBy} key on {@link Seance#getExercices()}.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Groupage en superset/biset : exercices consécutifs partageant le même {@code blockId}
     * sont enchaînés sans repos. NULL = exercice isolé.
     */
    @Column(name = "block_id")
    private Long blockId;

    /**
     * Nature du groupage : {@code "SUPERSET"} (exos antagonistes) ou {@code "BISET"}
     * (même groupe musculaire). NULL quand l'exercice est isolé.
     */
    @Column(name = "block_type", length = 16)
    private String blockType;

    /**
     * The start time of the exercise.
     */
    private LocalDateTime startTime;

    /**
     * The end time of the exercise.
     */
    private LocalDateTime endTime;

    /**
     * The session (Seance) during which this exercise is performed.
     * Ignored during JSON serialization to prevent infinite recursion.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seance_id")
    private Seance seance;

    // Lien optionnel vers la définition standardisée — null pour les exercices saisis en texte libre
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_definition_id")
    private ExerciceDefinition definition;

    /**
     * The list of sets (Series) performed during this exercise.
     */
    @OneToMany(mappedBy = "exercice", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 30)
    @Builder.Default
    private List<Serie> series = new ArrayList<>();

    /**
     * Helper method to add a set to this exercise, maintaining the bidirectional relationship.
     *
     * @param serie The set to add.
     */
    public void addSerie(Serie serie) {
        if (this.series == null) {
            this.series = new ArrayList<>();
        }
        this.series.add(serie);
        serie.setExercice(this);
    }
}
