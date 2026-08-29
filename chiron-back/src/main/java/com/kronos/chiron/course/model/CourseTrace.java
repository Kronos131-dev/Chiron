package com.kronos.chiron.course.model;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "course_trace")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "points", nullable = false, columnDefinition = "TEXT")
    private String points;

    @Column(name = "nb_points", nullable = false)
    private int nbPoints;

    @Column(name = "distance_m", nullable = false)
    private double distanceM;

    @Column(name = "duree_s", nullable = false)
    private int dureeS;

    @Column(name = "denivele_positif_m", nullable = false)
    private double denivelePositifM;

    @Column(name = "splits", columnDefinition = "TEXT")
    private String splits;

    @Column(name = "objectif_distance_m")
    private Double objectifDistanceM;

    @Column(name = "objectif_duree_s")
    private Integer objectifDureeS;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
