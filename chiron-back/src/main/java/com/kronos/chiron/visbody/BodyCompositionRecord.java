package com.kronos.chiron.visbody;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kronos.chiron.entity.Utilisateur;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Un scan de composition corporelle Visbody, importé depuis un rapport PDF.
 * {@code mesureLe} correspond au « Temps de détection » du rapport ; la paire
 * (utilisateur, mesureLe) est unique pour rendre les ré-imports idempotents.
 */
@Entity
@Table(name = "body_composition_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BodyCompositionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    @JsonIgnore
    private Utilisateur utilisateur;

    @Column(name = "mesure_le", nullable = false)
    private LocalDateTime mesureLe;

    @Column(name = "note")
    private Integer note;

    // Composition globale (kg sauf indication).
    @Column(name = "poids")              private Double poids;
    @Column(name = "masse_musculaire")   private Double masseMusculaire;
    @Column(name = "mms")                private Double mms;            // musculaire squelettique
    @Column(name = "mgc")                private Double mgc;            // masse grasse
    @Column(name = "mmc")                private Double mmc;            // masse maigre
    @Column(name = "tgc_pct")            private Double tgcPct;         // taux de graisse (%)
    @Column(name = "imc")                private Double imc;
    @Column(name = "rth")                private Double rth;
    @Column(name = "mb_kcal")            private Double mbKcal;
    @Column(name = "age_metabolique")    private Double ageMetabolique;
    @Column(name = "graisse_viscerale")  private Double graisseViscerale;
    @Column(name = "eau_totale")         private Double eauTotale;
    @Column(name = "eau_intra")          private Double eauIntra;
    @Column(name = "eau_extra")          private Double eauExtra;
    @Column(name = "ratio_ecw_tbw")      private Double ratioEcwTbw;
    @Column(name = "masse_proteine")     private Double masseProteine;
    @Column(name = "sel_inorganique")    private Double selInorganique;

    // Masse grasse par segment (kg).
    @Column(name = "mgc_bras_gauche")    private Double mgcBrasGauche;
    @Column(name = "mgc_bras_droit")     private Double mgcBrasDroit;
    @Column(name = "mgc_tronc")          private Double mgcTronc;
    @Column(name = "mgc_jambe_gauche")   private Double mgcJambeGauche;
    @Column(name = "mgc_jambe_droite")   private Double mgcJambeDroite;

    // Masse musculaire par segment (kg).
    @Column(name = "muscle_bras_gauche") private Double muscleBrasGauche;
    @Column(name = "muscle_bras_droit")  private Double muscleBrasDroit;
    @Column(name = "muscle_tronc")       private Double muscleTronc;
    @Column(name = "muscle_jambe_gauche")private Double muscleJambeGauche;
    @Column(name = "muscle_jambe_droite")private Double muscleJambeDroite;

    @Column(name = "source", nullable = false)
    @Builder.Default
    private String source = "VISBODY_PDF";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
