package com.kronos.chiron.course;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

// WHY: les boutons du casque n'aboutissaient jamais — l'application de musique possède la session
// media et garde la touche pour elle. Le guetteur écoute donc en permanence, pour que l'athlète
// n'ait rien à toucher. Il est écrit autour d'une seule contrainte, énoncée dans ecarterLeCasque.
public final class Guetteur {

    public interface Ecouteur {
        void entendu(String texte, boolean definitif);

        void indisponible(String raison);

        // WHY: sans ces deux signaux, un guetteur muet est indiscernable d'un guetteur qui
        // n'entend personne. Le compte des ecoutes lancees dit si le moteur demarre, le code
        // d'erreur dit pourquoi il s'arrete — c'est ce qui distingue un micro casse d'un athlete
        // silencieux, et rien d'autre ne le peut depuis l'ecran.
        void ecouteLancee();

        void erreurMoteur(int code);
    }

    private static final long RELANCE_MS = 200;
    private static final long RELANCE_APRES_ERREUR_MS = 1000;
    private static final long RELANCE_MAX_MS = 15000;
    private static final long REPRISE_APRES_PAROLE_MS = 400;

    private final Context context;
    private final Ecouteur ecouteur;
    private final String langue;
    private final Handler principal = new Handler(Looper.getMainLooper());

    private SpeechRecognizer moteur;
    private boolean voulu = false;
    private boolean suspendu = false;
    private boolean enEcoute = false;
    private long reculMs = RELANCE_APRES_ERREUR_MS;
    private String dernierPartiel = "";

    public Guetteur(Context context, String langue, Ecouteur ecouteur) {
        this.context = context;
        this.langue = langue;
        this.ecouteur = ecouteur;
    }

    // WHY: la reconnaissance embarquée date d'Android 12, et sa disponibilité réelle tient au
    // modèle de langue téléchargé sur l'appareil, pas au numéro de version. Refuser le réseau est
    // délibéré : une sortie se court là où il n'y en a pas, et une heure de micro téléversé
    // coûterait la batterie et les données pour un service qui échouerait au premier tunnel.
    public static boolean possible(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
        }
        return true;
    }

    public boolean actif() {
        return voulu;
    }

    public void demarrer() {
        principal.post(() -> {
            if (voulu) return;
            if (!possible(context)) {
                ecouteur.indisponible(raisonIndisponible());
                return;
            }
            voulu = true;
            suspendu = false;
            reculMs = RELANCE_APRES_ERREUR_MS;
            ouvrir();
        });
    }

    public void arreter() {
        principal.post(() -> {
            voulu = false;
            suspendu = false;
            libererLeMoteur();
        });
    }

    // WHY: Chiron parle dans les oreilles pendant que le micro est sur la hanche, mais l'athlète
    // peut courir sans écouteurs — la voix sort alors du haut-parleur, contre le micro. Se taire
    // pendant qu'on parle est le seul moyen sûr de ne pas s'écouter soi-même.
    public void suspendre() {
        principal.post(() -> {
            if (!voulu || suspendu) return;
            suspendu = true;
            couper();
        });
    }

    public void reprendre() {
        principal.post(() -> {
            if (!voulu || !suspendu) return;
            suspendu = false;
            relancer(REPRISE_APRES_PAROLE_MS);
        });
    }

    private void ouvrir() {
        if (!voulu || suspendu || enEcoute) return;
        if (moteur == null) {
            try {
                moteur = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            } catch (Exception erreur) {
                abandonner("moteur");
                return;
            }
            moteur.setRecognitionListener(new Auditeur());
        }
        ecarterLeCasque();
        dernierPartiel = "";
        enEcoute = true;
        try {
            moteur.startListening(intention());
            ecouteur.ecouteLancee();
        } catch (Exception erreur) {
            enEcoute = false;
            relancer(reculer());
        }
    }

    // WHY: c'est la contrainte qui gouverne tout le reste. Un casque Bluetooth n'expose que deux
    // profils exclusifs : A2DP, stéréo pleine qualité, et HFP/SCO, mono 8 kHz. Ouvrir le micro DU
    // CASQUE force la bascule en SCO, et la musique de l'athlète tombe en qualité téléphone pour
    // toute la sortie. Android n'y passe que si l'application le réclame ; le micro intégré du
    // téléphone est un chemin séparé du lien Bluetooth. On ne réclame donc jamais SCO —
    // ni startBluetoothSco, ni setCommunicationDevice, ni MODE_IN_COMMUNICATION — et on annule la
    // sélection qu'un autre écran de l'application aurait pu laisser derrière lui.
    private void ecarterLeCasque() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;
        try {
            audio.clearCommunicationDevice();
        } catch (Exception ignore) {}
    }

    private Intent intention() {
        Intent intention = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intention.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intention.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale());
        intention.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intention.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intention.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intention.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        return intention;
    }

    private String locale() {
        return "en".equals(langue) ? Locale.US.toLanguageTag() : Locale.FRANCE.toLanguageTag();
    }

    private void relancer(long delai) {
        if (!voulu || suspendu) return;
        principal.postDelayed(this::ouvrir, delai);
    }

    // WHY: le silence est l'état normal d'un guetteur, pas une panne : le moteur rend NO_MATCH ou
    // SPEECH_TIMEOUT à chaque fois que personne n'a parlé, et il faut le relancer aussitôt. Une
    // vraie panne, elle, se répète — d'où le recul qui double, pour qu'un moteur cassé ne tourne
    // pas à vide pendant une heure en vidant la batterie.
    private long reculer() {
        long actuel = reculMs;
        reculMs = Math.min(RELANCE_MAX_MS, reculMs * 2);
        return actuel;
    }

    private void couper() {
        principal.removeCallbacksAndMessages(null);
        if (moteur == null || !enEcoute) {
            enEcoute = false;
            return;
        }
        enEcoute = false;
        try {
            moteur.cancel();
        } catch (Exception ignore) {}
    }

    private void abandonner(String raison) {
        voulu = false;
        libererLeMoteur();
        ecouteur.indisponible(raison);
    }

    // WHY: destroy() appelé depuis onResults ou onError s'exécute dans la pile du moteur lui-même,
    // ce qui le fait planter sur plusieurs implémentations. La destruction est donc toujours
    // reportée au tour de boucle suivant — même règle que dans Ecoute.
    private void libererLeMoteur() {
        principal.removeCallbacksAndMessages(null);
        enEcoute = false;
        final SpeechRecognizer mourant = moteur;
        moteur = null;
        if (mourant == null) return;
        principal.post(() -> {
            try {
                mourant.cancel();
                mourant.destroy();
            } catch (Exception ignore) {}
        });
    }

    private String raisonIndisponible() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "permission";
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "version";
        return "modele";
    }

    private static String premier(Bundle resultats) {
        ArrayList<String> mots = resultats == null
            ? null
            : resultats.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return mots == null || mots.isEmpty() ? "" : mots.get(0);
    }

    private final class Auditeur implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {}

        @Override
        public void onBeginningOfSpeech() {}

        @Override
        public void onRmsChanged(float rms) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {}

        @Override
        public void onError(int erreur) {
            enEcoute = false;
            ecouteur.erreurMoteur(erreur);
            if (erreur == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                abandonner("permission");
                return;
            }
            if (
                erreur == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                erreur == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
            ) {
                abandonner("modele");
                return;
            }
            boolean silence =
                erreur == SpeechRecognizer.ERROR_NO_MATCH ||
                erreur == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
            if (silence) {
                reculMs = RELANCE_APRES_ERREUR_MS;
                relancer(RELANCE_MS);
                return;
            }
            relancer(reculer());
        }

        @Override
        public void onResults(Bundle resultats) {
            enEcoute = false;
            reculMs = RELANCE_APRES_ERREUR_MS;
            String texte = premier(resultats);
            if (texte.isEmpty()) texte = dernierPartiel;
            if (!texte.isEmpty()) ecouteur.entendu(texte, true);
            relancer(RELANCE_MS);
        }

        @Override
        public void onPartialResults(Bundle resultats) {
            String texte = premier(resultats);
            if (texte.isEmpty()) return;
            dernierPartiel = texte;
            ecouteur.entendu(texte, false);
        }

        @Override
        public void onEvent(int type, Bundle params) {}
    }
}
