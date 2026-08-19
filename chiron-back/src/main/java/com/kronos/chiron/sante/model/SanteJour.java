package com.kronos.chiron.sante.model;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sante_jour", uniqueConstraints = @UniqueConstraint(name = "uk_sante_jour_user_date", columnNames = {
        "utilisateur_id", "date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanteJour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "pas")
    private Integer pas;

    @Column(name = "distance_m")
    private Double distanceM;

    @Column(name = "calories_totales")
    private Integer caloriesTotales;

    @Column(name = "calories_actives")
    private Integer caloriesActives;

    @Column(name = "minutes_zone_active")
    private Integer minutesZoneActive;

    @Column(name = "minutes_zone_bruleuse")
    private Integer minutesZoneBruleuse;

    @Column(name = "minutes_zone_cardio")
    private Integer minutesZoneCardio;

    @Column(name = "minutes_zone_pic")
    private Integer minutesZonePic;

    @Column(name = "fc_repos")
    private Integer fcRepos;

    @Column(name = "fc_min")
    private Integer fcMin;

    @Column(name = "fc_moyenne")
    private Double fcMoyenne;

    @Column(name = "fc_max")
    private Integer fcMax;

    @Column(name = "vfc_ms")
    private Double vfcMs;

    @Column(name = "vfc_sommeil_profond_ms")
    private Double vfcSommeilProfondMs;

    @Column(name = "vo2_max")
    private Double vo2Max;

    @Column(name = "niveau_aptitude")
    private String niveauAptitude;

    @Column(name = "charge_cardio")
    private Double chargeCardio;

    @Column(name = "spo2_pct")
    private Double spo2Pct;

    @Column(name = "frequence_respiratoire")
    private Double frequenceRespiratoire;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    @PreUpdate
    void onSync() {
        syncedAt = LocalDateTime.now();
    }
}
