package com.kronos.chiron.entity;

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

    /**
     * The list of sets (Series) performed during this exercise.
     */
    @OneToMany(mappedBy = "exercice", cascade = CascadeType.ALL, orphanRemoval = true)
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
