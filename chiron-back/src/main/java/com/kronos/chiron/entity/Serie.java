package com.kronos.chiron.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    // ── Paramètres cardio (nullable — renseignés uniquement pour les exercices cardio) ──

    /** Durée de l'effort en minutes (marche, course, rameur, SkiErg). */
    @Column(name = "duree_min")
    private Double dureeMin;

    /** Distance parcourue en mètres (rameur, SkiErg). */
    @Column(name = "distance_m")
    private Double distanceM;

    /** Vitesse en km/h (marche, course). */
    @Column(name = "allure_kmh")
    private Double allureKmh;

    /** Pente en pourcentage (marche, course). */
    @Column(name = "pente_pct")
    private Double pentePct;

    /** Calories brûlées estimées, calculées côté serveur à la sauvegarde. */
    @Column(name = "calories")
    private Double calories;

    /**
     * The exercise to which this set belongs.
     * Ignored during JSON serialization to prevent infinite recursion.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_id")
    private Exercice exercice;
    
    @OneToMany(mappedBy = "serie", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Degressif> degressifs = new ArrayList<>();
    
    public void addDegressif(Degressif degressif) {
        degressifs.add(degressif);
        degressif.setSerie(this);
    }
}
