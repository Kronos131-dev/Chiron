package com.kronos.chiron.course;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommandesTest {

    @Test
    public void normaliser_accentsEtPonctuation_rendUneChaineNue() {
        assertEquals("passe a 5 30", Commandes.normaliser("Passe à 5:30 !"));
    }

    @Test
    public void normaliser_apostropheEtEspacesMultiples_lesRameneAUnSeulEspace() {
        assertEquals("ou j en suis", Commandes.normaliser("Où  j’en   suis ?"));
    }

    // WHY: le moteur ecrit « mets-moi » et « vas-y ». Les motifs attendent deux mots : sans cette
    // chute du trait d'union, la moitie des tournures naturelles ne declenche rien.
    @Test
    public void normaliser_traitDUnion_ledissout() {
        assertEquals("mets moi a 5 30", Commandes.normaliser("Mets-moi à 5:30"));
        assertEquals("vas y", Commandes.normaliser("Vas-y"));
    }

    @Test
    public void interpreter_passeACinqTrente_rendLaCible() {
        Commandes.Commande commande = Commandes.interpreter("passe à 5:30");

        assertNotNull(commande);
        assertEquals("cible", commande.nom);
        assertEquals(5.5, commande.cibleMinParKm, 0.001);
    }

    @Test
    public void interpreter_allureCinqMinutesTrente_rendLaCible() {
        Commandes.Commande commande = Commandes.interpreter("allure cinq minutes trente");

        assertNotNull(commande);
        assertEquals("cible", commande.nom);
        assertEquals(5.5, commande.cibleMinParKm, 0.001);
    }

    @Test
    public void interpreter_cinqEtDemi_rendLaCible() {
        Commandes.Commande commande = Commandes.interpreter("vise cinq et demi");

        assertNotNull(commande);
        assertEquals(5.5, commande.cibleMinParKm, 0.001);
    }

    @Test
    public void interpreter_allureSansNombre_annonceLAllure() {
        Commandes.Commande commande = Commandes.interpreter("quelle allure");

        assertNotNull(commande);
        assertEquals("allure", commande.nom);
        assertNull(commande.cibleMinParKm);
    }

    @Test
    public void interpreter_verbesDeRythme_rendentLeSensDuReglage() {
        assertEquals("plusVite", Commandes.interpreter("accélère").nom);
        assertEquals("moinsVite", Commandes.interpreter("ralentis").nom);
    }

    @Test
    public void interpreter_pauseEtReprise_sontDistinguees() {
        assertEquals("pause", Commandes.interpreter("pause").nom);
        assertEquals("reprendre", Commandes.interpreter("on reprend").nom);
    }

    @Test
    public void interpreter_questionsSurLaSortie_rendentLesAnnonces() {
        assertEquals("distance", Commandes.interpreter("combien j'ai parcouru").nom);
        assertEquals("duree", Commandes.interpreter("depuis combien de temps").nom);
        assertEquals("bilan", Commandes.interpreter("bilan").nom);
    }

    // WHY: « arrête » appartient a la pause et « arrête la course » a la fin de sortie. Les
    // confondre clot une course que l'athlete voulait suspendre le temps d'un feu rouge.
    @Test
    public void interpreter_arreterLaCourse_terminePlutotQueMettreEnPause() {
        assertEquals("terminer", Commandes.interpreter("arrête la course").nom);
        assertEquals("terminer", Commandes.interpreter("termine").nom);
        assertEquals("pause", Commandes.interpreter("arrête").nom);
    }

    @Test
    public void interpreter_phraseSansOrdre_rendNull() {
        assertNull(Commandes.interpreter("il fait beau ce matin"));
        assertNull(Commandes.interpreter(""));
        assertNull(Commandes.interpreter(null));
    }

    @Test
    public void detecterMotCle_interjectionEtOrdre_rendLaSuite() {
        Commandes.Reveil reveil = Commandes.detecterMotCle("Hey Chiron, mets-moi à 5:30");

        assertNotNull(reveil);
        assertTrue(reveil.avecInterjection);
        assertEquals("mets moi a 5 30", reveil.suite);
    }

    @Test
    public void detecterMotCle_graphiesApprochantes_sontAcceptees() {
        assertNotNull(Commandes.detecterMotCle("et chirot pause"));
        assertNotNull(Commandes.detecterMotCle("ok kiron bilan"));
        assertNotNull(Commandes.detecterMotCle("hé chiron"));
    }

    // WHY: le nom seul n'ouvre pas le micro. Un « chiron » isole entendu dans une conversation
    // ne doit pas faire repondre « J'ecoute » au fond d'une poche ; suivi d'un ordre, si.
    @Test
    public void detecterMotCle_nomSeulSansInterjection_leSignale() {
        Commandes.Reveil reveil = Commandes.detecterMotCle("chiron pause");

        assertNotNull(reveil);
        assertFalse(reveil.avecInterjection);
        assertEquals("pause", reveil.suite);
    }

    @Test
    public void detecterMotCle_motCleSeul_rendUneSuiteVide() {
        Commandes.Reveil reveil = Commandes.detecterMotCle("hey chiron");

        assertNotNull(reveil);
        assertTrue(reveil.avecInterjection);
        assertEquals("", reveil.suite);
    }

    @Test
    public void detecterMotCle_phraseSansLeNom_rendNull() {
        assertNull(Commandes.detecterMotCle("il reste trois kilomètres"));
        assertNull(Commandes.detecterMotCle(""));
    }

    @Test
    public void estUneConfirmation_ouiEtSesVariantes_valident() {
        assertTrue(Commandes.estUneConfirmation("oui"));
        assertTrue(Commandes.estUneConfirmation("vas-y confirme"));
        assertFalse(Commandes.estUneConfirmation("non laisse tomber"));
        assertFalse(Commandes.estUneConfirmation(""));
    }

    @Test
    public void lireAllure_horsDesBornes_rendNull() {
        assertNull(Commandes.lireAllure("passe a 1"));
        assertNull(Commandes.lireAllure("passe a 40"));
        assertNull(Commandes.lireAllure("passe a rien"));
    }
}
