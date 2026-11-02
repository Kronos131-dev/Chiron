package com.kronos.chiron.exportation.service.classeur;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

record BornesAxe(double minimum, double maximum, double pas) {

    private static final double MARGE_RELATIVE = 0.25;
    private static final double MARGE_MINIMALE = 1;
    private static final double MARGE_SUR_VALEUR_UNIQUE = 0.1;
    private static final int GRADUATIONS_VISEES = 5;
    private static final double[] PAS_LISIBLES = {1, 2, 2.5, 5, 10};

    static BornesAxe pour(List<Double> valeurs) {
        List<Double> presentes = valeurs.stream().filter(Objects::nonNull).toList();
        if (presentes.isEmpty()) {
            return null;
        }
        double min = Collections.min(presentes);
        double max = Collections.max(presentes);
        double etendue = max - min;
        double marge = etendue > 0
                ? etendue * MARGE_RELATIVE
                : Math.max(MARGE_MINIMALE, Math.abs(max) * MARGE_SUR_VALEUR_UNIQUE);
        double pas = pasLisible((etendue + 2 * marge) / GRADUATIONS_VISEES);
        double bas = Math.floor((min - marge) / pas) * pas;
        if (min >= 0) {
            bas = Math.max(0, bas);
        }
        double haut = Math.ceil((max + marge) / pas) * pas;
        return new BornesAxe(bas, haut, pas);
    }

    private static double pasLisible(double brut) {
        double puissance = Math.pow(10, Math.floor(Math.log10(brut)));
        double fraction = brut / puissance;
        for (double lisible : PAS_LISIBLES) {
            if (fraction <= lisible) {
                return lisible * puissance;
            }
        }
        return PAS_LISIBLES[PAS_LISIBLES.length - 1] * puissance;
    }
}
