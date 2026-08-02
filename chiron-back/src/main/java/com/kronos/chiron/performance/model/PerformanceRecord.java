package com.kronos.chiron.performance.model;

import com.kronos.chiron.seance.model.ExerciseType;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "performance_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "exercise_type", nullable = false, length = 50)
    private ExerciseType exerciseType;

    @Column(nullable = false)
    private Double poids;

    @Column(name = "nombre_reps", nullable = false)
    private Integer nombreReps;

    @Column(name = "rm1_estime", nullable = false)
    private Double rm1Estime;

    @Column(name = "ratio_performance")
    private Double ratioPerformance;

    @Column(name = "poids_corporel")
    private Double poidsCorporel;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();
}
