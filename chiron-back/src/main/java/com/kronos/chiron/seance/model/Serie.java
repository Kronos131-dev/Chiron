package com.kronos.chiron.seance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    private double poids;

    private int nombreReps;

    private String commentaire;

    @Column(name = "duree_min")
    private Double dureeMin;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(name = "allure_kmh")
    private Double allureKmh;

    @Column(name = "pente_pct")
    private Double pentePct;

    @Column(name = "calories")
    private Double calories;

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
