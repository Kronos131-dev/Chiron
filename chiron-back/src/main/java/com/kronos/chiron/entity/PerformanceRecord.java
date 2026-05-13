package com.kronos.chiron.entity;

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

    /** Weight entered by user: bar weight for compound lifts, extra weight for bodyweight exercises. */
    @Column(nullable = false)
    private Double poids;

    @Column(name = "nombre_reps", nullable = false)
    private Integer nombreReps;

    /** Estimated 1RM calculated at record time. */
    @Column(name = "rm1_estime", nullable = false)
    private Double rm1Estime;

    /** 1RM / bodyweight ratio (null if bodyweight unknown). */
    @Column(name = "ratio_performance")
    private Double ratioPerformance;

    /** Snapshot of bodyweight used for ratio calculation. */
    @Column(name = "poids_corporel")
    private Double poidsCorporel;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();
}
