package com.kronos.chiron.course;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// WHY: jumeau de util/commandes-vocales.ts. L'interpretation vivait uniquement en TypeScript :
// le service publiait le transcript, la WebView decidait, puis rappelait le natif. Ecran
// verrouille cette WebView peut etre gelee, et l'ordre mourait au milieu du chemin. Le service
// savait deja executer chaque action ; il lui manquait de savoir laquelle.
public final class Commandes {

    public static final class Commande {

        public final String nom;
        public final Double cibleMinParKm;

        Commande(String nom, Double cibleMinParKm) {
            this.nom = nom;
            this.cibleMinParKm = cibleMinParKm;
        }
    }

    // WHY: le nom seul n'ouvre pas le micro, l'interjection oui. « Chiron, pause » agit parce
    // qu'un ordre suit ; un « chiron » isole entendu dans une conversation ne doit pas faire
    // repondre « J'ecoute » au fond d'une poche.
    public static final class Reveil {

        public final boolean avecInterjection;
        public final String suite;

        Reveil(boolean avecInterjection, String suite) {
            this.avecInterjection = avecInterjection;
            this.suite = suite;
        }
    }

    private static final double CIBLE_MIN = 2.5;
    private static final double CIBLE_MAX = 15;
    private static final double SECONDES_PAR_MINUTE = 60;

    private static final Map<String, Integer> NOMBRES = new HashMap<>();

    static {
        NOMBRES.put("zero", 0);
        NOMBRES.put("un", 1);
        NOMBRES.put("une", 1);
        NOMBRES.put("deux", 2);
        NOMBRES.put("trois", 3);
        NOMBRES.put("quatre", 4);
        NOMBRES.put("cinq", 5);
        NOMBRES.put("six", 6);
        NOMBRES.put("sept", 7);
        NOMBRES.put("huit", 8);
        NOMBRES.put("neuf", 9);
        NOMBRES.put("dix", 10);
        NOMBRES.put("onze", 11);
        NOMBRES.put("douze", 12);
        NOMBRES.put("treize", 13);
        NOMBRES.put("quatorze", 14);
        NOMBRES.put("quinze", 15);
        NOMBRES.put("seize", 16);
        NOMBRES.put("vingt", 20);
        NOMBRES.put("trente", 30);
        NOMBRES.put("quarante", 40);
        NOMBRES.put("cinquante", 50);
        NOMBRES.put("one", 1);
        NOMBRES.put("two", 2);
        NOMBRES.put("three", 3);
        NOMBRES.put("four", 4);
        NOMBRES.put("five", 5);
        NOMBRES.put("seven", 7);
        NOMBRES.put("eight", 8);
        NOMBRES.put("nine", 9);
        NOMBRES.put("ten", 10);
        NOMBRES.put("eleven", 11);
        NOMBRES.put("twelve", 12);
        NOMBRES.put("fifteen", 15);
        NOMBRES.put("twenty", 20);
        NOMBRES.put("thirty", 30);
        NOMBRES.put("forty", 40);
        NOMBRES.put("fifty", 50);
    }

    private static final Pattern DIACRITIQUES = Pattern.compile("[\\u0300-\\u036f]");
    private static final Pattern PONCTUATION = Pattern.compile("[.,!?;:]");
    private static final Pattern LIAISONS = Pattern.compile("[-'’]");
    private static final Pattern ESPACES = Pattern.compile("\\s+");

    private static final Pattern ET_DEMI = Pattern.compile("(\\d{1,2})\\s*(?:min[a-z]*)?\\s*et demi");
    private static final Pattern SEPARATEUR = Pattern.compile("(\\d{1,2})\\s*[:/h]\\s*(\\d{1,2})");
    private static final Pattern AVEC_MINUTES = Pattern.compile("(\\d{1,2})\\s*min[a-z]*\\s*(\\d{1,2})?");
    private static final Pattern DEUX_NOMBRES = Pattern.compile("(\\d{1,2})\\s+(\\d{1,2})");
    private static final Pattern SEUL = Pattern.compile("(\\d{1,2})");

    private static final Pattern PLUS_VITE = Pattern.compile(
        "(plus vite|accelere|acceler|augmente|monte le rythme|faster|speed up)"
    );
    private static final Pattern MOINS_VITE = Pattern.compile(
        "(moins vite|ralenti|ralentis|baisse le rythme|calme|slower|slow down|ease)"
    );
    private static final Pattern PAUSE = Pattern.compile(
        "(pause|arrete|arret|stoppe|stop|attends|halte)"
    );
    private static final Pattern REPRENDRE = Pattern.compile(
        "(repren|reprend|repart|c est reparti|on y va|continue|resume|go|restart)"
    );
    private static final Pattern CIBLE = Pattern.compile(
        "(cible|objectif|vise|passe a|mets? moi a|regle|target|set)"
    );
    private static final Pattern ALLURE = Pattern.compile("(allure|rythme|vitesse|pace|tempo)");
    private static final Pattern DISTANCE = Pattern.compile(
        "(distance|combien.*(parcouru|fait|km|kilometre)|how far)"
    );
    private static final Pattern DUREE = Pattern.compile(
        "(duree|depuis combien|temps|chrono|time|how long)"
    );
    private static final Pattern BILAN = Pattern.compile("(bilan|resume|ou j en suis|status|recap)");

    // WHY: « arrete » et « stop » appartiennent deja a la pause. Terminer se reconnait donc a
    // des tournures entieres, jamais au verbe seul — confondre les deux clot une sortie que
    // l'athlete voulait seulement suspendre le temps d'un feu rouge.
    private static final Pattern TERMINER = Pattern.compile(
        "(termin|c est fini|j ai fini|arrete la course|arreter la course|stop la course|" +
        "finish the run|end the run)"
    );

    private static final Pattern CHIFFRE = Pattern.compile("\\d");

    private static final Pattern CONFIRMATION = Pattern.compile(
        "(\\boui\\b|confirme|vas y|va s y|c est bon|d accord|\\byes\\b|confirm|go ahead)"
    );

    // WHY: la liste des graphies vient de ce que le moteur rend vraiment, pas de ce qu'on
    // prononce. « Hey Chiron » revient en « et chiron », « he chirot » ou « ok kiron » selon le
    // vent et le souffle ; l'ecran affiche le transcript brut pour que cette liste s'allonge sur
    // preuve plutot que sur intuition.
    private static final Pattern MOT_CLE = Pattern.compile(
        "(?:\\b(hey|eh|he|et|est|ok|okay|allo|hello|salut)\\s+)?" +
        "\\b(chiron|chirons|chirone|chiro|chirot|chiraud|chirac|kiron|kyron|shiron|shiro|siron)\\b"
    );

    private Commandes() {}

    // WHY: le moteur rend « cinq minutes trente », « 5 minutes 30 » ou « 5:30 » selon l'humeur du
    // micro et le bruit ambiant. Tout est ramene a une meme chaine sans accent ni ponctuation
    // avant d'etre reconnu, sinon la moitie des formulations tombe a cote. Le trait d'union tombe
    // avec l'apostrophe : le moteur ecrit « mets-moi » et « vas-y », que les motifs attendent en
    // deux mots.
    public static String normaliser(String transcript) {
        if (transcript == null) return "";
        String minuscule = transcript.toLowerCase(Locale.ROOT);
        String sansAccent = DIACRITIQUES
            .matcher(Normalizer.normalize(minuscule, Normalizer.Form.NFD))
            .replaceAll("");
        String sansPonctuation = PONCTUATION.matcher(sansAccent).replaceAll(" ");
        String sansLiaison = LIAISONS.matcher(sansPonctuation).replaceAll(" ");
        return ESPACES.matcher(sansLiaison).replaceAll(" ").trim();
    }

    public static Reveil detecterMotCle(String transcript) {
        String texte = normaliser(transcript);
        if (texte.isEmpty()) return null;
        Matcher trouve = MOT_CLE.matcher(texte);
        if (!trouve.find()) return null;
        return new Reveil(trouve.group(1) != null, texte.substring(trouve.end()).trim());
    }

    public static boolean estUneConfirmation(String transcript) {
        String texte = normaliser(transcript);
        return !texte.isEmpty() && CONFIRMATION.matcher(texte).find();
    }

    // WHY: l'ordre compte. « passe a cinq minutes trente » contient « allure » dans certaines
    // formulations, et « reprends l'allure » contient les deux : la commande la plus specifique
    // doit etre reconnue avant la plus generale, sinon l'athlete obtient une annonce au lieu d'un
    // reglage.
    public static Commande interpreter(String transcript) {
        String texte = normaliser(transcript);
        if (texte.isEmpty()) return null;

        if (TERMINER.matcher(texte).find()) return new Commande("terminer", null);

        if (CIBLE.matcher(texte).find()) {
            Double cible = lireAllure(texte);
            if (cible != null) return new Commande("cible", cible);
        }

        if (ALLURE.matcher(texte).find() && CHIFFRE.matcher(motsEnNombres(texte)).find()) {
            Double cible = lireAllure(texte);
            if (cible != null) return new Commande("cible", cible);
        }

        if (PLUS_VITE.matcher(texte).find()) return new Commande("plusVite", null);
        if (MOINS_VITE.matcher(texte).find()) return new Commande("moinsVite", null);
        if (REPRENDRE.matcher(texte).find()) return new Commande("reprendre", null);
        if (PAUSE.matcher(texte).find()) return new Commande("pause", null);
        if (BILAN.matcher(texte).find()) return new Commande("bilan", null);
        if (DISTANCE.matcher(texte).find()) return new Commande("distance", null);
        if (DUREE.matcher(texte).find()) return new Commande("duree", null);
        if (ALLURE.matcher(texte).find()) return new Commande("allure", null);

        return null;
    }

    // WHY: « cinq minutes trente » se dicte aussi « cinq trente » ou « cinq et demi ». Les trois
    // designent la meme allure, et un coureur essouffle emploie la plus courte.
    public static Double lireAllure(String texte) {
        String t = motsEnNombres(texte);

        Matcher etDemi = ET_DEMI.matcher(t);
        if (etDemi.find()) return borner(entier(etDemi.group(1)) + 0.5);

        Matcher separateur = SEPARATEUR.matcher(t);
        if (separateur.find()) {
            return borner(
                entier(separateur.group(1)) + entier(separateur.group(2)) / SECONDES_PAR_MINUTE
            );
        }

        Matcher avecMinutes = AVEC_MINUTES.matcher(t);
        if (avecMinutes.find()) {
            double secondes = avecMinutes.group(2) == null ? 0 : entier(avecMinutes.group(2));
            return borner(entier(avecMinutes.group(1)) + secondes / SECONDES_PAR_MINUTE);
        }

        Matcher deuxNombres = DEUX_NOMBRES.matcher(t);
        if (deuxNombres.find()) {
            return borner(
                entier(deuxNombres.group(1)) + entier(deuxNombres.group(2)) / SECONDES_PAR_MINUTE
            );
        }

        Matcher seul = SEUL.matcher(t);
        if (seul.find()) return borner(entier(seul.group(1)));

        return null;
    }

    private static String motsEnNombres(String texte) {
        String[] mots = texte.split(" ");
        StringBuilder rendu = new StringBuilder();
        for (int i = 0; i < mots.length; i++) {
            if (i > 0) rendu.append(' ');
            Integer nombre = NOMBRES.get(mots[i]);
            rendu.append(nombre == null ? mots[i] : String.valueOf(nombre));
        }
        return rendu.toString();
    }

    private static int entier(String brut) {
        return Integer.parseInt(brut, 10);
    }

    private static Double borner(double minParKm) {
        if (!Double.isFinite(minParKm)) return null;
        if (minParKm < CIBLE_MIN || minParKm > CIBLE_MAX) return null;
        return Math.round(minParKm * SECONDES_PAR_MINUTE) / SECONDES_PAR_MINUTE;
    }
}
