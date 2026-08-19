package com.kronos.chiron.sante.model;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sante_sommeil", uniqueConstraints = @UniqueConstraint(name = "uk_sante_sommeil_user_debut", columnNames = {
        "utilisateur_id", "debut"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanteSommeil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "debut", nullable = false)
    private LocalDateTime debut;

    @Column(name = "fin")
    private LocalDateTime fin;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "sieste", nullable = false)
    @Builder.Default
    private boolean sieste = false;

    @Column(name = "stades_disponibles", nullable = false)
    @Builder.Default
    private boolean stadesDisponibles = false;

    @Column(name = "minutes_endormi")
    private Integer minutesEndormi;

    @Column(name = "minutes_eveille")
    private Integer minutesEveille;

    @Column(name = "minutes_avant_endormissement")
    private Integer minutesAvantEndormissement;

    @Column(name = "minutes_apres_reveil")
    private Integer minutesApresReveil;

    @Column(name = "minutes_profond")
    private Integer minutesProfond;

    @Column(name = "minutes_leger")
    private Integer minutesLeger;

    @Column(name = "minutes_paradoxal")
    private Integer minutesParadoxal;

    @Column(name = "minutes_agite")
    private Integer minutesAgite;

    @Column(name = "nb_reveils")
    private Integer nbReveils;

    @Column(name = "fc_sommeil_moyenne")
    private Double fcSommeilMoyenne;

    @Column(name = "score")
    private Integer score;

    @Column(name = "score_duree")
    private Integer scoreDuree;

    @Column(name = "score_composition")
    private Integer scoreComposition;

    @Column(name = "score_restauration")
    private Integer scoreRestauration;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    @PreUpdate
    void onSync() {
        syncedAt = LocalDateTime.now();
    }
}
