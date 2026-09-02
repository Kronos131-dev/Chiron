package com.kronos.chiron.seance.model;

import lombok.Getter;

@Getter
public enum ExerciseType {

    DEVELOPPE_COUCHE("Développé Couché", false, null,
            new double[]{0.70, 0.85, 1.00, 1.15, 1.30, 1.45, 1.60}),

    SQUAT("Squat", false, null,
            new double[]{0.95, 1.15, 1.30, 1.50, 1.70, 1.90, 2.10}),

    SOULEVE_DE_TERRE("Soulevé de Terre", false, null,
            new double[]{1.10, 1.30, 1.50, 1.75, 2.00, 2.20, 2.40}),

    TRACTIONS("Tractions", true, null,
            new double[]{1.05, 1.15, 1.25, 1.40, 1.55, 1.65, 1.75}),

    DIPS("Dips", true, null,
            new double[]{1.10, 1.20, 1.35, 1.50, 1.65, 1.80, 1.95}),

    // WHY: les paliers d'une course se mesurent en km/h, et non en secondes, pour que l'échelle
    // reste croissante comme celle des barres — un athlète plus fort a un ratio plus grand, un
    // coureur plus rapide a une vitesse plus grande, et le même parcours de seuils sert les deux.
    // 8 km/h sur 5 km, c'est 37 min 30 s ; 18 km/h, c'est 16 min 40 s. L'écart entre les deux
    // distances suit la formule de Riegel (t2 = t1 × (d2/d1)^1,06), qui coûte environ 4 % de
    // vitesse au doublement de la distance : 50 min sur 10 km valent le même palier que 24 min
    // sur 5 km.
    COURSE_5KM("5 km", false, 5.0,
            new double[]{8.0, 9.5, 11.0, 12.5, 14.0, 16.0, 18.0}),

    COURSE_10KM("10 km", false, 10.0,
            new double[]{7.5, 9.0, 10.5, 12.0, 13.5, 15.5, 17.5});

    private final String nom;
    private final boolean bodyweightExercise;
    private final Double distanceKm;
    private final double[] thresholds;

    ExerciseType(String nom, boolean bodyweightExercise, Double distanceKm, double[] thresholds) {
        this.nom = nom;
        this.bodyweightExercise = bodyweightExercise;
        this.distanceKm = distanceKm;
        this.thresholds = thresholds;
    }

    public boolean isCourse() {
        return distanceKm != null;
    }

    public double vitesseKmh(int tempsSecondes) {
        return distanceKm / (tempsSecondes / 3600.0);
    }

    public int tempsSecondesPour(double vitesseKmh) {
        return (int) Math.round(distanceKm / vitesseKmh * 3600.0);
    }
}
