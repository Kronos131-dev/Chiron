package com.kronos.chiron.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exercice_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciceDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomFr;

    @Column(nullable = false)
    private String nomEn;

    @Column(columnDefinition = "TEXT")
    private String descriptionFr;

    @Column(columnDefinition = "TEXT")
    private String descriptionEn;

    private String gifPath;

    @Enumerated(EnumType.STRING)
    private MuscleGroup musclePrincipal;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercice_definition_muscles_secondaires",
            joinColumns = @JoinColumn(name = "exercice_definition_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "muscle")
    @BatchSize(size = 50)
    @Builder.Default
    private List<MuscleGroup> musclesSecondaires = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private TypeEquipement typeEquipement;

    @Enumerated(EnumType.STRING)
    private NiveauDifficulte difficulte;

    // ID originel dans le dataset exercisedb-pro — garantit l'idempotence de l'import
    @Column(unique = true)
    private String externalId;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Long usageCount = 0L;
}
