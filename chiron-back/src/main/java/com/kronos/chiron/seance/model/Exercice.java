package com.kronos.chiron.seance.model;

import com.kronos.chiron.exercice.model.ExerciceDefinition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    private String nom;

    private String commentaire;

    @Column(name = "unilateral", nullable = false)
    @Builder.Default
    private boolean unilateral = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "block_id")
    private Long blockId;

    @Column(name = "block_type", length = 16)
    private String blockType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seance_id")
    private Seance seance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercice_definition_id")
    private ExerciceDefinition definition;

    @OneToMany(mappedBy = "exercice", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 30)
    @Builder.Default
    private List<Serie> series = new ArrayList<>();

    public void addSerie(Serie serie) {
        if (this.series == null) {
            this.series = new ArrayList<>();
        }
        this.series.add(serie);
        serie.setExercice(this);
    }
}
