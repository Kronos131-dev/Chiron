package com.kronos.chiron.course;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

public final class Ecoute {

    public interface Ecouteur {
        void transcription(String texte, boolean definitif);

        void echec(String raison);
    }

    private static final long DUREE_MAX_MS = 9000;

    private final Context context;
    private final Ecouteur ecouteur;
    private final Handler principal = new Handler(Looper.getMainLooper());
    private final String langue;

    private SpeechRecognizer moteur;
    private boolean active = false;
    private String dernierTexte = "";

    public Ecoute(Context context, String langue, Ecouteur ecouteur) {
        this.context = context;
        this.langue = langue;
        this.ecouteur = ecouteur;
    }

    public boolean disponible() {
        return (
            SpeechRecognizer.isRecognitionAvailable(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        );
    }

    public boolean active() {
        return active;
    }

    public void demarrer() {
        principal.post(this::demarrerSurLePrincipal);
    }

    // WHY: SpeechRecognizer ne fonctionne que depuis le thread principal, et le service doit
    // porter foregroundServiceType="microphone" — sans ce type Android 12+ coupe le micro dès
    // que l'écran se verrouille, ce qui est précisément le moment où on en a besoin.
    private void demarrerSurLePrincipal() {
        if (active || !disponible()) {
            if (!disponible()) ecouteur.echec("indisponible");
            return;
        }
        dernierTexte = "";
        moteur = SpeechRecognizer.createSpeechRecognizer(context);
        moteur.setRecognitionListener(
            new RecognitionListener() {
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
                    boolean avaitDuTexte = !dernierTexte.isEmpty();
                    arreter();
                    if (avaitDuTexte) ecouteur.transcription(dernierTexte, true);
                    else ecouteur.echec("erreur-" + erreur);
                }

                @Override
                public void onResults(Bundle resultats) {
                    String texte = premier(resultats);
                    arreter();
                    ecouteur.transcription(texte.isEmpty() ? dernierTexte : texte, true);
                }

                @Override
                public void onPartialResults(Bundle resultats) {
                    String texte = premier(resultats);
                    if (texte.isEmpty()) return;
                    dernierTexte = texte;
                    ecouteur.transcription(texte, false);
                }

                @Override
                public void onEvent(int type, Bundle params) {}
            }
        );

        Intent intention = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intention.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intention.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale());
        intention.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intention.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intention.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());

        active = true;
        moteur.startListening(intention);
        principal.postDelayed(this::cloturer, DUREE_MAX_MS);
    }

    private String locale() {
        return "en".equals(langue) ? Locale.US.toLanguageTag() : Locale.FRANCE.toLanguageTag();
    }

    private String premier(Bundle resultats) {
        ArrayList<String> mots = resultats == null
            ? null
            : resultats.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return mots == null || mots.isEmpty() ? "" : mots.get(0);
    }

    public void cloturer() {
        principal.post(() -> {
            if (!active) return;
            if (moteur != null) moteur.stopListening();
        });
    }

    // WHY: destroy() appelé depuis onResults ou onError s'exécute dans la pile du moteur
    // lui-même, ce qui le fait planter sur plusieurs implémentations. La destruction est donc
    // toujours reportée au tour de boucle suivant.
    public void arreter() {
        active = false;
        principal.removeCallbacksAndMessages(null);
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
}
