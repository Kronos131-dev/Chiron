package com.kronos.chiron.course;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MesureTest {

    private static final long DEPART = 1_700_000_000_000L;
    private static final double METRE_EN_DEGRE_LAT = 180 / (Math.PI * Mesure.RAYON_TERRE_M);

    private Point apres(double metres, long secondes) {
        return new Point(48 + metres * METRE_EN_DEGRE_LAT, 2, DEPART + secondes * 1000, null, false);
    }

    private Point repriseApres(double metres, long secondes) {
        return new Point(48 + metres * METRE_EN_DEGRE_LAT, 2, DEPART + secondes * 1000, null, true);
    }

    @Test
    public void distanceM_deuxPointsAlignes_sommeLesSegments() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(500, 150));
        mesure.ajouter(apres(1000, 300));

        assertEquals(1000, mesure.distanceM(), 1);
    }

    @Test
    public void distanceM_segmentDeCoupure_ignoreLaPause() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(1000, 300));
        mesure.ajouter(repriseApres(3000, 900));
        mesure.ajouter(apres(3500, 1050));

        assertEquals(1500, mesure.distanceM(), 1);
    }

    @Test
    public void allureCouranteKmh_pauseExclue_neDiluePasLaFenetre() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(1000, 300));
        mesure.ajouter(repriseApres(3000, 900));
        mesure.ajouter(apres(3100, 930));

        assertEquals(12, mesure.allureCouranteKmh(), 0.5);
    }

    @Test
    public void kilometresFranchis_justeAvantLeKilometre_resteAZero() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(999, 300));

        assertEquals(0, mesure.kilometresFranchis());
    }

    @Test
    public void tropProche_deplacementSousLeSeuil_rejetteLePoint() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));

        assertTrue(mesure.tropProche(apres(2, 5)));
    }

    @Test
    public void allureMoyenneKmh_millesMetresEnCinqMinutes_donneDouzeKmh() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(1000, 300));

        assertEquals(12, mesure.allureMoyenneKmh(300_000), 0.1);
    }

    @Test
    public void formaterAllure_douzeKmh_donneCinqMinutes() {
        assertEquals("5:00", Phrases.formaterAllure(12));
    }

    @Test
    public void formaterChrono_plusDUneHeure_afficheLesHeures() {
        assertEquals("1:01:05", Phrases.formaterChrono(3665));
    }
}
