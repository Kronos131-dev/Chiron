package com.kronos.chiron.visbody.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
public class VisbodyReport {

    private String idLabel;
    private String maskedEmail;
    private String sexe;
    private Double tailleCm;
    private Integer age;
    private LocalDateTime mesureLe;
    private Integer note;

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

    private Double mgcBrasGauche;
    private Double mgcBrasDroit;
    private Double mgcTronc;
    private Double mgcJambeGauche;
    private Double mgcJambeDroite;

    private Double muscleBrasGauche;
    private Double muscleBrasDroit;
    private Double muscleTronc;
    private Double muscleJambeGauche;
    private Double muscleJambeDroite;
}
