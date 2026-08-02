package com.kronos.chiron.coach.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Outils d'explication des fonctionnalités de l'application Chiron.
 * Permet à l'agent IA de répondre fidèlement aux questions « comment marche X ? »
 * en s'appuyant sur des descriptions curatées plutôt que sur de l'improvisation.
 */
@Component
public class AppGuideTools {

    private static final Map<String, String> GUIDES = new LinkedHashMap<>();

    static {
        GUIDES.put("overview",
                "Chiron est une app de musculation pilotée par un coach IA (moi). Tu peux :\n" +
                "• Discuter avec moi (texte ou voix) pour enregistrer tes séances, demander conseil, ou analyser tes données.\n" +
                "• Consulter ton journal (« Annales ») pour revoir l'historique semaine par semaine.\n" +
                "• Créer et lancer des programmes-templates réutilisables, avec supersets/bisets.\n" +
                "• Suivre ta progression dans le Trésor : 8 paliers d'Éphèbe à Olympien selon ton ratio force/poids.\n" +
                "• Échanger dans l'Agora avec d'autres athlètes, ou nommer un coach qui peut éditer tes programmes.\n" +
                "• Lier Olympus (nutrition) et Fitbit (sommeil/activité) pour un suivi complet.");

        GUIDES.put("chat",
                "Le chat est ta porte d'entrée pour tout. Tu peux me parler en langage naturel :\n" +
                "• « Démarre une séance pectoraux » → je crée la séance et j'attends les séries.\n" +
                "• « 80 kg 8 reps » → j'enregistre la série dans l'exercice courant.\n" +
                "• « Donne-moi mon PR sur le développé couché » → je consulte ton historique.\n" +
                "• « Comment marche X ? » → j'explique la feature.\n" +
                "Pendant une séance, garde le focus dialogue : pas besoin de naviguer dans les menus.");

        GUIDES.put("voice",
                "Le gros bouton micro en bas du chat lance la dictée (Web Speech API, français). \n" +
                "Idéal pendant l'effort : annonce « 100 kg 5 reps » entre tes séries, ça enregistre tout seul.\n" +
                "Astuce : un dictionnaire interne corrige les confusions classiques (crêpe → reps, etc.).");

        GUIDES.put("journal",
                "Les Annales (icône livre dans le menu) regroupent toutes tes séances réellement effectuées,\n" +
                "triées par semaine. Tu peux y revoir le volume total, cliquer sur une séance pour voir\n" +
                "le détail (exos, séries, reps, poids). C'est ton historique de combat.");

        GUIDES.put("programmes",
                "Les Programmes (icône liste) sont des templates de séances réutilisables.\n" +
                "• « Nouveau programme » → tu ajoutes des exercices depuis la bibliothèque ou en libre.\n" +
                "• Drag-and-drop pour réordonner.\n" +
                "• Bouton 🔗 sur une carte exercice → choisis « Superset » ou « Biset » pour grouper\n" +
                "  l'exo avec le suivant (ils s'enchaînent sans repos pendant la séance).\n" +
                "• « Commencer » lance une vraie séance à partir du template.\n" +
                "• En mode Coach, tu peux éditer le programme d'un autre athlète.");

        GUIDES.put("tresor",
                "Le Trésor est l'écran de progression force. Pour chaque grand exercice (développé,\n" +
                "squat, soulevé de terre, tractions, dips), il calcule ton 1RM estimé et te place\n" +
                "sur une échelle de 8 paliers :\n" +
                "  Novice : Éphèbe, Argonaute\n" +
                "  Athlète : Hoplite, Myrmidon, Spartiate\n" +
                "  Légende : Héros, Demi-Dieu, Olympien\n" +
                "Le palier dépend du ratio (poids soulevé / poids corporel). Renseigne ton poids\n" +
                "corporel en haut de l'écran pour activer le calcul.");

        GUIDES.put("agora",
                "L'Agora (icône groupe) liste les autres athlètes. Tu peux :\n" +
                "• Rendre ton profil public ou privé (paramètres).\n" +
                "• Consulter le profil d'un athlète public : son palier, ses programmes, son volume.\n" +
                "• Le nommer comme ton « Coach » : il pourra éditer tes programmes et voir tes\n" +
                "  données même si ton profil est privé.");

        GUIDES.put("coach",
                "Un Coach est un autre utilisateur de Chiron à qui tu donnes les droits d'éditer\n" +
                "tes programmes. Tu choisis tes coachs dans les paramètres ou via leur profil.\n" +
                "Inversement, si quelqu'un t'a nommé coach, tu verras dans son profil un bouton\n" +
                "« Éditer » sur ses programmes : tu deviens responsable de sa planif.");

        GUIDES.put("nutrition",
                "Si tu lies ton compte Olympus (réglages → intégrations), je peux lire :\n" +
                "• Tes apports journaliers (kcal, protéines, glucides, lipides).\n" +
                "• Tes objectifs nutritionnels.\n" +
                "• L'équilibre de tes macros et l'évolution de ton poids.\n" +
                "Demande-moi par exemple « combien de protéines hier ? » et j'irai chercher.");

        GUIDES.put("fitbit",
                "Si tu lies Google Health/Fitbit (réglages → intégrations), je peux lire :\n" +
                "• Tes pas et activité journalière.\n" +
                "• Ton sommeil récent (durée, qualité).\n" +
                "• Ta fréquence cardiaque au repos.\n" +
                "Utile pour adapter l'intensité d'une séance à ton état de forme.");

        GUIDES.put("recovery",
                "Tu peux m'annoncer chaque matin ton état (humeur, fatigue, courbatures) via\n" +
                "« je me sens X aujourd'hui ». Je le note dans ton journal d'état. Ensuite, demande-moi\n" +
                "« quel type de séance aujourd'hui ? » : je croiserai état + dernier muscle entraîné +\n" +
                "récupération pour proposer.");

        GUIDES.put("supersets",
                "Dans le builder de programme, à côté du bouton supprimer d'un exercice, tu as 🔗 :\n" +
                "clique → choisis « Superset » (exos antagonistes) ou « Biset » (même muscle).\n" +
                "L'exo et son suivant deviennent un groupe encadré dans la liste, étiqueté\n" +
                "« SUPERSET A » ou « BISET A ». Pendant la séance, ces exos s'enchaînent sans repos.\n" +
                "Bouton « Dissocier » dans l'en-tête du groupe pour le défaire.");
    }

    @Tool("Explique une feature de l'app Chiron. Paramètre 'feature' : overview, chat, voice, journal, programmes, tresor, agora, coach, nutrition, fitbit, recovery, supersets. En cas de doute, passe 'overview' (renvoie la vue globale + la liste des features).")
    public String expliquerFonctionnement(String feature) {
        String key = feature == null ? "" : feature.trim().toLowerCase(Locale.ROOT);
        String guide = GUIDES.get(key);
        if (guide != null) {
            return guide;
        }
        return GUIDES.get("overview")
                + "\n\nFeatures disponibles (utilise l'une de ces clés en argument) : "
                + String.join(", ", GUIDES.keySet());
    }
}
