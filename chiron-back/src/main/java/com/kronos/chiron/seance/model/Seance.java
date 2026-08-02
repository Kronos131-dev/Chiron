package com.kronos.chiron.seance.model;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class Seance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titre;

    @Column(columnDefinition = "TEXT")
    private String note;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private int weekNumber;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isModele;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int displayOrder;

    @OneToMany(mappedBy = "seance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<Exercice> exercices = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Utilisateur utilisateur;

    public void addExercice(Exercice exercice) {
        if (this.exercices == null) {
            this.exercices = new ArrayList<>();
        }
        this.exercices.add(exercice);
        exercice.setSeance(this);
    }
}
