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

    private static final Pattern CHIFFRE = Pattern.compile("\\d");

    // WHY: la cascade « premier motif gagnant » exigeait que le moteur rende le mot exact. Il rend
    // « allur », « pose », « billan » — et l'ordre tombait a cote sans que rien ne le dise. Chaque
    // commande porte donc ses formulations, et c'est la meilleure ressemblance qui gagne.
    // La priorite ne departage que les ex aequo : « passe a cinq trente » contient « allure » dans
    // certaines formulations, et un reglage vaut mieux qu'une annonce.
    private static final class Motif {

        final String nom;
        final int priorite;
        final String[] formulations;

        Motif(String nom, int priorite, String... formulations) {
            this.nom = nom;
            this.priorite = priorite;
            this.formulations = formulations;
        }
    }

    private static final Motif[] VOCABULAIRE = {
        new Motif("terminer", 3, "termine", "terminer", "c est fini", "j ai fini",
            "arrete la course", "arreter la course", "stop la course", "finish the run",
            "end the run"),
        new Motif("cible", 3, "cible", "objectif", "vise", "passe a", "mets moi a", "met moi a",
            "regle", "target", "set"),
        new Motif("plusVite", 2, "plus vite", "accelere", "acceler", "augmente",
            "monte le rythme", "faster", "speed up"),
        new Motif("moinsVite", 2, "moins vite", "ralenti", "ralentis", "baisse le rythme",
            "calme", "slower", "slow down"),
        new Motif("reprendre", 2, "reprends", "reprend", "reprendre", "repart", "c est reparti",
            "on y va", "continue", "resume", "restart"),
        // WHY: « pause » et « pose » sont homophones en francais, et le moteur choisit le mot le
        // plus courant. La ressemblance de graphie ne les rapproche pas assez — deux lettres sur
        // cinq — donc la variante est nommee, comme le fait deja util/vocabulaire-vocal.ts pour
        // le vocabulaire de la musculation.
        new Motif("pause", 2, "pause", "pose", "poser", "arrete", "stop", "stoppe", "attends",
            "halte"),
        new Motif("bilan", 2, "bilan", "resume", "ou j en suis", "status", "recap", "le point"),
        new Motif("distance", 2, "distance", "combien de kilometres", "combien de km",
            "combien j ai parcouru", "combien j ai fait", "how far"),
        new Motif("duree", 2, "duree", "chrono", "depuis combien de temps", "combien de temps",
            "how long"),
        new Motif("allure", 1, "allure", "rythme", "vitesse", "pace", "tempo")
    };

    // WHY: en dessous de cinq caracteres, la tolerance devient un piege : « sept » est a une
    // lettre de « set », et un simple nombre declencherait un reglage de cible. Les formulations
    // courtes ne se reconnaissent donc qu'a l'identique.
    private static final int LONGUEUR_MIN_APPROCHEE = 5;
    private static final double SEUIL_RESSEMBLANCE = 0.75;

    private static final String[] NOMS_DU_COACH = {
        "chiron", "chirons", "chirone", "chiro", "chirot", "chiraud", "chirac", "kiron", "kyron",
        "shiron", "shiro", "siron"
    };

    private static final String[] INTERJECTIONS = {
        "hey", "eh", "he", "et", "est", "ok", "okay", "allo", "hello", "salut"
    };

    private Commandes() {}

    // WHY: la version precedente intervertissait la diagonale et le voisin de gauche, et ne
    // remettait jamais dp[0] a i. Elle rendait des distances qui n'en etaient pas — c'est
    // vraisemblablement pourquoi le rattrapage approche ajoute pour le mot-cle n'a jamais rien
    // rattrape en course. Tout le reste du fichier repose sur cette fonction : elle a son test.
    static int distance(String a, String b) {
        int[] ligne = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) ligne[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int diagonale = ligne[0];
            ligne[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int precedent = ligne[j];
                int cout = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                ligne[j] = Math.min(Math.min(ligne[j] + 1, ligne[j - 1] + 1), diagonale + cout);
                diagonale = precedent;
            }
        }
        return ligne[b.length()];
    }

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

    // WHY: le moteur colle l'interjection au nom et rend « echiron » d'un seul tenant, ou le
    // coupe en « e chiron ». Comparer mot a mot ratait le premier cas, et un simple decoupage
    // ratait le second : la recherche se fait donc aussi sur la chaine privee de ses espaces,
    // ou les deux graphies se rejoignent.
    public static Reveil detecterMotCle(String transcript) {
        String texte = normaliser(transcript);
        if (texte.isEmpty()) return null;

        String[] mots = texte.split(" ");
        for (int i = 0; i < mots.length; i++) {
            if (!estLeNomDuCoach(mots[i])) continue;
            boolean avecInterjection = i > 0 && estUneInterjection(mots[i - 1]);
            String suite = i + 1 < mots.length ? joindre(mots, i + 1) : "";
            return new Reveil(avecInterjection, suite);
        }

        // WHY: dernier recours pour « echiron » seul, que le decoupage en mots ne rattrape pas.
        // La suite est alors inconnue, mais le mot-cle n'est plus exige pour agir : ce qui
        // compte est de savoir que l'athlete s'est adresse au coach.
        String colle = texte.replace(" ", "");
        for (String nom : NOMS_DU_COACH) {
            for (String interjection : INTERJECTIONS) {
                if (ressemble(colle, interjection + nom)) return new Reveil(true, "");
            }
            if (ressemble(colle, nom)) return new Reveil(false, "");
        }
        return null;
    }

    public static boolean estUneConfirmation(String transcript) {
        String texte = normaliser(transcript);
        if (texte.isEmpty()) return false;
        for (String mot : texte.split(" ")) {
            if (mot.equals("oui") || mot.equals("yes") || mot.equals("confirme")) return true;
        }
        return texte.contains("vas y") || texte.contains("va s y") || texte.contains("c est bon")
            || texte.contains("d accord") || texte.contains("go ahead");
    }

    private static boolean estLeNomDuCoach(String mot) {
        for (String nom : NOMS_DU_COACH) {
            if (ressemble(mot, nom)) return true;
        }
        return false;
    }

    private static boolean estUneInterjection(String mot) {
        for (String interjection : INTERJECTIONS) {
            if (mot.equals(interjection)) return true;
        }
        return false;
    }

    private static String joindre(String[] mots, int depuis) {
        StringBuilder rendu = new StringBuilder();
        for (int i = depuis; i < mots.length; i++) {
            if (rendu.length() > 0) rendu.append(' ');
            rendu.append(mots[i]);
        }
        return rendu.toString();
    }

    // WHY: le rapport, pas la distance brute. Une lettre fausse sur cinq n'a pas le meme poids
    // qu'une lettre fausse sur quinze, et un seuil exprime en distance absolue laisse passer
    // n'importe quoi sur les mots longs tout en etranglant les courts.
    private static boolean ressemble(String entendu, String attendu) {
        if (entendu.equals(attendu)) return true;
        if (attendu.length() < LONGUEUR_MIN_APPROCHEE) return false;
        int longueur = Math.max(entendu.length(), attendu.length());
        if (longueur == 0) return false;
        double rapport = 1.0 - (double) distance(entendu, attendu) / longueur;
        return rapport >= SEUIL_RESSEMBLANCE;
    }

    public static Commande interpreter(String transcript) {
        String texte = normaliser(transcript);
        if (texte.isEmpty()) return null;

        Motif meilleur = null;
        double meilleurScore = 0;
        for (Motif motif : VOCABULAIRE) {
            double score = scorer(texte, motif);
            if (score < SEUIL_RESSEMBLANCE) continue;
            if (meilleur == null || score > meilleurScore
                || (score == meilleurScore && motif.priorite > meilleur.priorite)) {
                meilleur = motif;
                meilleurScore = score;
            }
        }
        if (meilleur == null) return null;

        // WHY: « passe a cinq trente » et « allure cinq trente » demandent tous deux un reglage,
        // pas une annonce. C'est la presence d'un nombre lisible comme une allure qui fait la
        // difference, et elle se teste apres coup plutot que dans l'ordre des motifs.
        if (meilleur.nom.equals("cible") || meilleur.nom.equals("allure")) {
            Double cible = CHIFFRE.matcher(motsEnNombres(texte)).find() ? lireAllure(texte) : null;
            if (cible != null) return new Commande("cible", cible);
            if (meilleur.nom.equals("cible")) return null;
        }
        return new Commande(meilleur.nom, null);
    }

    // WHY: une formulation de plusieurs mots doit etre comparee a une fenetre de meme longueur,
    // sinon « allure » noyee dans une phrase de dix mots ne ressemble plus a rien.
    private static double scorer(String texte, Motif motif) {
        String[] mots = texte.split(" ");
        double meilleur = 0;
        for (String formulation : motif.formulations) {
            int taille = formulation.split(" ").length;
            for (int i = 0; i + taille <= mots.length; i++) {
                String fenetre = joindreFenetre(mots, i, taille);
                if (!ressemble(fenetre, formulation)) continue;
                int longueur = Math.max(fenetre.length(), formulation.length());
                double rapport = longueur == 0
                    ? 0
                    : 1.0 - (double) distance(fenetre, formulation) / longueur;
                if (rapport > meilleur) meilleur = rapport;
            }
        }
        return meilleur;
    }

    private static String joindreFenetre(String[] mots, int depuis, int taille) {
        StringBuilder rendu = new StringBuilder();
        for (int i = depuis; i < depuis + taille; i++) {
            if (rendu.length() > 0) rendu.append(' ');
            rendu.append(mots[i]);
        }
        return rendu.toString();
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
