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
    public void paliersFranchis_justeAvantLeKilometre_resteAZero() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(999, 300));

        assertEquals(0, mesure.paliersFranchis());
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
    public void allureDuDernierPalierKmh_deuxKilometresInegaux_rendCelleDuDernier() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(1000, 240));
        assertEquals(15, mesure.allureDuDernierPalierKmh(), 0.1);

        mesure.ajouter(apres(2000, 840));
        assertEquals(2, mesure.paliersFranchis());
        assertEquals(6, mesure.allureDuDernierPalierKmh(), 0.1);
    }

    @Test
    public void dureeDuDernierPalierMs_franchissementAuMilieuDUnSegment_interpoleLInstant() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(2000, 600));

        assertEquals(2, mesure.paliersFranchis());
        assertEquals(300_000, mesure.dureeDuDernierPalierMs(), 1000);
    }

    @Test
    public void dureeDuDernierPalierMs_pauseEntreDeuxKilometres_neGonflePasLeSplit() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(1000, 300));
        mesure.ajouter(repriseApres(1000, 1200));
        mesure.ajouter(apres(2000, 1500));

        assertEquals(2, mesure.paliersFranchis());
        assertEquals(300_000, mesure.dureeDuDernierPalierMs(), 1000);
    }

    @Test
    public void dureeDuDernierPalierMs_palierNonFranchi_rendZero() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(500, 150));

        assertEquals(0, mesure.paliersFranchis());
        assertEquals(0, mesure.dureeDuDernierPalierMs());
        assertEquals(0, mesure.allureDuDernierPalierKmh(), 0.001);
    }

    // WHY: l'intervalle d'annonce se regle de 100 m a 1 km. Sous le kilometre, chaque palier
    // doit tomber a sa distance et porter l'allure du segment qui vient d'etre couru.
    @Test
    public void fixerIntervalle_troisCentsMetres_compteLesPaliersEtLeurAllure() {
        Mesure mesure = new Mesure();
        mesure.fixerIntervalle(300);
        mesure.ajouter(apres(0, 0));
        // WHY: le point tombe au-dela du palier, jamais dessus. Le haversine rend 899,9998 m
        // pour une latitude calculee a 900 m, et une assertion posee sur la borne exacte
        // echouerait sur l'arrondi plutot que sur le comportement.
        mesure.ajouter(apres(950, 285));

        assertEquals(3, mesure.paliersFranchis());
        assertEquals(900, mesure.distanceDesPaliersM(), 0.001);
        assertEquals(90_000, mesure.dureeDuDernierPalierMs(), 1000);
        assertEquals(12, mesure.allureDuDernierPalierKmh(), 0.2);
    }

    // WHY: resserrer l'intervalle en pleine course ne doit pas rejouer d'un coup toutes les
    // annonces des paliers deja parcourus.
    @Test
    public void fixerIntervalle_changeEnPleineCourse_rebaseLeCompteur() {
        Mesure mesure = new Mesure();
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(1000, 300));
        assertEquals(1, mesure.paliersFranchis());

        mesure.fixerIntervalle(200);
        assertEquals(5, mesure.paliersFranchis());

        mesure.ajouter(apres(1250, 375));
        assertEquals(6, mesure.paliersFranchis());
        assertEquals(60_000, mesure.dureeDuDernierPalierMs(), 1000);
    }

    @Test
    public void instantObjectifMs_objectifAuMilieuDUnSegment_interpoleLInstant() {
        Mesure mesure = new Mesure();
        mesure.fixerObjectif(1000);
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(2000, 600));

        assertEquals(300_000, mesure.instantObjectifMs().longValue(), 1000);
    }

    @Test
    public void instantObjectifMs_objectifJamaisAtteint_resteNul() {
        Mesure mesure = new Mesure();
        mesure.fixerObjectif(5000);
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(2000, 600));

        assertTrue(mesure.instantObjectifMs() == null);
    }

    // WHY: l'objectif ne borne pas la course. Les points qui suivent doivent continuer à
    // compter, et l'instant relevé ne doit plus bouger.
    @Test
    public void instantObjectifMs_pointsApresLObjectif_neDeplacentPasLInstant() {
        Mesure mesure = new Mesure();
        mesure.fixerObjectif(1000);
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(2000, 600));
        long releve = mesure.instantObjectifMs();
        mesure.ajouter(apres(3000, 900));

        assertEquals(releve, mesure.instantObjectifMs().longValue());
        assertEquals(3000, mesure.distanceM(), 1);
    }

    // WHY: le service Android n'est detruit que quelques secondes apres l'arret. Une seconde
    // sortie lancee entre-temps retombe sur la meme instance, et sans remise a zero le journal
    // recevait la somme des deux courses.
    @Test
    public void reinitialiser_secondeSortie_repartDeZero() {
        Mesure mesure = new Mesure();
        mesure.fixerObjectif(1000);
        mesure.ajouter(apres(0, 0));
        mesure.ajouter(apres(2000, 600));

        mesure.reinitialiser();
        mesure.ajouter(apres(0, 3600));
        mesure.ajouter(apres(500, 3750));

        assertEquals(500, mesure.distanceM(), 1);
        assertEquals(2, mesure.nbPoints());
        assertEquals(0, mesure.paliersFranchis());
        assertTrue(mesure.instantObjectifMs() == null);
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
