package com.kronos.chiron.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing a specific set (serie) performed during an exercise.
 * It tracks the weight lifted, the number of repetitions, and any optional comments.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Serie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The weight lifted during this set.
     */
    private double poids;

    /**
     * The number of repetitions completed in this set.
     */
    private int nombreReps;

    /**
     * Optional comment or note regarding this specific set.
     */
    private String commentaire;

    /**
     * The exercise to which this set belongs.
     * Ignored during JSON serialization to prevent infinite recursion.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_id")
    private Exercice exercice;
}
