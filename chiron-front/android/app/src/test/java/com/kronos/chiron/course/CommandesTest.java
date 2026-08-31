package com.kronos.chiron.course;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommandesTest {

    // WHY: tout le rapprochement approche repose sur cette fonction. La version precedente
    // intervertissait la diagonale et le voisin de gauche : elle rendait des nombres, pas des
    // distances, et rien au-dessus ne pouvait marcher.
    @Test
    public void distance_casConnus_rendLaVraieDistance() {
        assertEquals(0, Commandes.distance("chiron", "chiron"));
        assertEquals(1, Commandes.distance("echiron", "chiron"));
        assertEquals(1, Commandes.distance("allur", "allure"));
        assertEquals(2, Commandes.distance("pose", "pause"));
        assertEquals(3, Commandes.distance("kitten", "sitting"));
        assertEquals(6, Commandes.distance("", "chiron"));
    }

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
        assertEquals("distance", Commandes.interpreter("combien j'ai fait").nom);
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

    // WHY: ces graphies ne sont pas inventees. Ce sont celles que le moteur rend vraiment quand
    // l'athlete parle essouffle, telephone dans la poche — et chacune tombait a cote de la
    // cascade de motifs exacts, en silence.
    @Test
    public void interpreter_erreursDEcouteReelles_restentComprises() {
        assertEquals("allure", Commandes.interpreter("allur").nom);
        assertEquals("pause", Commandes.interpreter("pose").nom);
        assertEquals("bilan", Commandes.interpreter("billan").nom);
        assertEquals("distance", Commandes.interpreter("distance parcourue").nom);
    }

    // WHY: la tolerance ne doit pas mordre sur les nombres. « sept » est a une lettre de « set »,
    // et le laisser passer ferait d'un chiffre dicte un reglage de cible.
    @Test
    public void interpreter_motCourtProche_neDeclenchePas() {
        assertNull(Commandes.interpreter("sept"));
        assertNull(Commandes.interpreter("il fait beau ce matin"));
    }

    // WHY: le moteur colle l'interjection au nom. C'est le transcript exact qui a fait dire a
    // l'athlete que la reconnaissance ne marchait pas.
    @Test
    public void detecterMotCle_interjectionColleeAuNom_estReconnue() {
        Commandes.Reveil reveil = Commandes.detecterMotCle("echiron allure");

        assertNotNull(reveil);
        assertEquals("allure", reveil.suite);
    }

    // WHY: l'ordre se passe desormais du nom du coach. Le service retire le nom quand il est la
    // et garde la phrase entiere quand il n'y est pas — les deux chemins menent au meme ordre.
    @Test
    public void detecterMotCle_avecLeNom_isoleLOrdre() {
        assertEquals("allure", Commandes.detecterMotCle("hey chiron allure").suite);
        assertEquals("mets moi a 5 30", Commandes.detecterMotCle("Chiron, mets-moi à 5:30").suite);
        assertNull(Commandes.detecterMotCle("allure"));
    }

    @Test
    public void lireAllure_horsDesBornes_rendNull() {
        assertNull(Commandes.lireAllure("passe a 1"));
        assertNull(Commandes.lireAllure("passe a 40"));
        assertNull(Commandes.lireAllure("passe a rien"));
    }
}
