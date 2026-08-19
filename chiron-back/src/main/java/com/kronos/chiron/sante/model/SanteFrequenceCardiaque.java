package com.kronos.chiron.sante.model;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sante_frequence_cardiaque", uniqueConstraints = @UniqueConstraint(name = "uk_sante_fc_user_horodatage", columnNames = {
        "utilisateur_id", "horodatage"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanteFrequenceCardiaque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "horodatage", nullable = false)
    private LocalDateTime horodatage;

    @Column(name = "fc_min")
    private Integer fcMin;

    @Column(name = "fc_moyenne")
    private Double fcMoyenne;

    @Column(name = "fc_max")
    private Integer fcMax;

    @Column(name = "nb_echantillons")
    private Integer nbEchantillons;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    @PreUpdate
    void onSync() {
        syncedAt = LocalDateTime.now();
    }
}
