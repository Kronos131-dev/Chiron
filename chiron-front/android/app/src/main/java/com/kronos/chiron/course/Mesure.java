package com.kronos.chiron.course;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Mesure {

    public static final double RAYON_TERRE_M = 6371008.8;
    public static final double KM_EN_METRES = 1000;
    public static final double PRECISION_MAX_M = 25;
    public static final double DEPLACEMENT_MIN_M = 3;

    private static final long FENETRE_ALLURE_MS = 30000;
    private static final double MS_PAR_SECONDE = 1000;
    private static final double SECONDES_PAR_HEURE = 3600;
    private static final int DECIMALES_DEGRES = 5;

    private final List<Point> points = new ArrayList<>();
    private final List<double[]> parcours = new ArrayList<>();

    private double cumulM = 0;
    private long pauseCumuleeMs = 0;
    private long depart = 0;

    public static double distanceHaversineM(Point a, Point b) {
        double lat1 = Math.toRadians(a.lat);
        double lat2 = Math.toRadians(b.lat);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(b.lon - a.lon);
        double h =
            Math.pow(Math.sin(deltaLat / 2), 2) +
            Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * RAYON_TERRE_M * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    public static double allureKmh(double distanceM, double dureeS) {
        if (dureeS <= 0 || distanceM <= 0) return 0;
        return distanceM / KM_EN_METRES / (dureeS / SECONDES_PAR_HEURE);
    }

    public static double arrondirDegres(double valeur) {
        double facteur = Math.pow(10, DECIMALES_DEGRES);
        return Math.round(valeur * facteur) / facteur;
    }

    // WHY: jumeau borné de mesurer() dans service/course-tracker.ts et de
    // CourseGeometrieServiceImpl.mesurer côté serveur. Le natif ne calcule que ce dont
    // l'annonce vocale et la notification ont besoin — ni splits, ni dénivelé, qui restent
    // l'affaire du backend, seule autorité du journal.
    public void ajouter(Point courant) {
        if (points.isEmpty()) {
            depart = courant.t;
            points.add(courant);
            parcours.add(new double[] { 0, 0 });
            return;
        }
        Point precedent = points.get(points.size() - 1);
        if (courant.coupure) {
            pauseCumuleeMs += courant.t - precedent.t;
        } else {
            cumulM += distanceHaversineM(precedent, courant);
        }
        points.add(courant);
        parcours.add(new double[] { cumulM, courant.t - depart - pauseCumuleeMs });
    }

    public boolean tropProche(Point candidat) {
        if (points.isEmpty()) return false;
        return distanceHaversineM(points.get(points.size() - 1), candidat) < DEPLACEMENT_MIN_M;
    }

    public double distanceM() {
        return cumulM;
    }

    public int kilometresFranchis() {
        return (int) Math.floor(cumulM / KM_EN_METRES);
    }

    public int nbPoints() {
        return points.size();
    }

    public List<Point> points() {
        return Collections.unmodifiableList(points);
    }

    // WHY: l'allure d'un point au suivant saute de 8 à 16 km/h sous les arbres. La fenêtre
    // glissante de trente secondes est ce qui rend le chiffre annonçable sans mentir.
    public double allureCouranteKmh() {
        if (parcours.size() < 2) return 0;
        double[] fin = parcours.get(parcours.size() - 1);
        double[] debut = parcours.get(0);
        for (int i = parcours.size() - 1; i >= 0; i--) {
            debut = parcours.get(i);
            if (fin[1] - parcours.get(i)[1] >= FENETRE_ALLURE_MS) break;
        }
        double dureeS = (fin[1] - debut[1]) / MS_PAR_SECONDE;
        return allureKmh(fin[0] - debut[0], Math.round(dureeS));
    }

    public double allureMoyenneKmh(long dureeMs) {
        return allureKmh(cumulM, Math.floor(dureeMs / MS_PAR_SECONDE));
    }
}
