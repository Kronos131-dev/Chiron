package com.kronos.chiron.sante.model;

import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sante_activite", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sante_activite_seance", columnNames = {"seance_id"}),
        @UniqueConstraint(name = "uk_sante_activite_user_debut_source", columnNames = {"utilisateur_id",
                "start_time", "source"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanteActivite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seance_id")
    private Seance seance;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private SourceActivite source;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_activite", nullable = false, length = 32)
    private TypeActivite typeActivite;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "calories_estimees", nullable = false)
    @Builder.Default
    private boolean caloriesEstimees = false;

    @Column(name = "fc_moyenne")
    private Double fcMoyenne;

    @Column(name = "fc_min")
    private Integer fcMin;

    @Column(name = "fc_max")
    private Integer fcMax;

    @Column(name = "minutes_zone_basse")
    private Integer minutesZoneBasse;

    @Column(name = "minutes_zone_bruleuse")
    private Integer minutesZoneBruleuse;

    @Column(name = "minutes_zone_cardio")
    private Integer minutesZoneCardio;

    @Column(name = "minutes_zone_pic")
    private Integer minutesZonePic;

    @Column(name = "minutes_zone_active")
    private Integer minutesZoneActive;

    @Column(name = "charge_cardio")
    private Double chargeCardio;

    @Column(name = "external_id")
    private String externalId;

    // WHY: dit que CETTE ligne est une séance qu'on a écrite dans Google Health, et pas un
    // exercice que la montre a détecté toute seule. C'est ce qui autorise à recopier les
    // chiffres que Google republie : il les a calculés sur l'intervalle qu'on lui a donné.
    // L'externalId ne suffirait pas — la synchronisation le remplit aussi depuis un exercice
    // détecté, sur une fenêtre plus courte que la vraie séance.
    @Column(name = "pousse_google", nullable = false)
    @Builder.Default
    private boolean pousseGoogle = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_enrichissement", nullable = false, length = 32)
    private StatutEnrichissement statutEnrichissement;

    @Column(name = "tentatives_enrichissement", nullable = false)
    @Builder.Default
    private int tentativesEnrichissement = 0;

    @Column(name = "prochaine_tentative_at")
    private LocalDateTime prochaineTentativeAt;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    @PreUpdate
    void onSync() {
        syncedAt = LocalDateTime.now();
    }
}
