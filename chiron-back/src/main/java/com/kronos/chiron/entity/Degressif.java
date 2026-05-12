package com.kronos.chiron.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing a specific drop set (degressif) performed during an exercise.
 * It tracks the weight lifted, the number of repetitions.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Degressif {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The weight lifted during this drop set.
     */
    private double poids;

    /**
     * The number of repetitions completed in this drop set.
     */
    private int nombreReps;

    /**
     * The serie to which this drop set belongs.
     * Ignored during JSON serialization to prevent infinite recursion.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serie_id")
    private Serie serie;
}
