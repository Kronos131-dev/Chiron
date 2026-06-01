package com.kronos.chiron.visbody;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Données extraites d'un rapport PDF Visbody. Tout est nullable : le parser
 * remplit ce qu'il trouve et laisse {@code null} le reste (parsing best-effort).
 */
@Getter
@Setter
@ToString
public class VisbodyReport {

    // En-tête / identité.
    private String idLabel;       // champ « ID » du rapport (ex. "Tellier")
    private String maskedEmail;   // ex. "octa****n1er@gmail..."
    private String sexe;          // "Homme" / "Femme"
    private Double tailleCm;
    private Integer age;
    private LocalDateTime mesureLe; // « Temps de détection »
    private Integer note;

    // Composition globale.
    private Double poids;
    private Double masseMusculaire;
    private Double mms;
    private Double mgc;
    private Double mmc;
    private Double tgcPct;
    private Double imc;
    private Double rth;
    private Double mbKcal;
    private Double ageMetabolique;
    private Double graisseViscerale;
    private Double eauTotale;
    private Double eauIntra;
    private Double eauExtra;
    private Double ratioEcwTbw;
    private Double masseProteine;
    private Double selInorganique;

    // Segments — masse grasse (kg).
    private Double mgcBrasGauche;
    private Double mgcBrasDroit;
    private Double mgcTronc;
    private Double mgcJambeGauche;
    private Double mgcJambeDroite;

    // Segments — masse musculaire (kg).
    private Double muscleBrasGauche;
    private Double muscleBrasDroit;
    private Double muscleTronc;
    private Double muscleJambeGauche;
    private Double muscleJambeDroite;
}
