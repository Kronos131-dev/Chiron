package com.kronos.chiron.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "etat_journalier",
        uniqueConstraints = @UniqueConstraint(name = "uk_etat_journalier_user_date", columnNames = {"utilisateur_id", "date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtatJournalier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    /** Heures de sommeil la nuit précédente. */
    @Column(name = "sommeil_heures")
    private Double sommeilHeures;

    /** Fatigue ressentie 1 (frais) → 5 (épuisé). */
    @Column(name = "fatigue")
    private Integer fatigue;

    /** Courbatures 1 (aucune) → 5 (très douloureux). */
    @Column(name = "courbatures")
    private Integer courbatures;

    /** Stress 1 (zen) → 5 (tendu). */
    @Column(name = "stress")
    private Integer stress;

    /** Énergie 1 (vide) → 5 (à fond). */
    @Column(name = "energie")
    private Integer energie;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
