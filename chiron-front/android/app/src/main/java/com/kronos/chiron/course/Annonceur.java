package com.kronos.chiron.course;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class Annonceur {

    private static final float HAUTEUR_MASCULINE = 0.85f;
    private static final float HAUTEUR_A_DEFAUT = 0.72f;
    private static final float DEBIT = 0.92f;
    private static final String RESPIRATION = "\\|";
    private static final long SILENCE_MS = 180;

    private final Context context;
    private final AudioManager audioManager;
    private final Deque<String> attente = new ArrayDeque<>();
    private final AtomicInteger enCours = new AtomicInteger(0);
    private final AtomicInteger compteur = new AtomicInteger(0);

    private TextToSpeech tts;
    private boolean pret = false;
    private boolean libere = false;
    private AudioFocusRequest demandeFocus;
    private final AudioManager.OnAudioFocusChangeListener focusListener = changement -> {};

    public Annonceur(Context context, String langue) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.tts = new TextToSpeech(context, statut -> preparer(statut, langue));
    }

    private void preparer(int statut, String langue) {
        if (libere || statut != TextToSpeech.SUCCESS) return;
        Locale locale = "en".equals(langue) ? Locale.US : Locale.FRANCE;
        tts.setLanguage(locale);
        // WHY: USAGE_ASSISTANCE_NAVIGATION_GUIDANCE est ce qui fait baisser la musique des
        // autres applications le temps de la phrase, puis la remonte — le comportement d'un
        // GPS de voiture. Le web n'avait aucune prise équivalente sur le focus audio.
        tts.setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        );
        tts.setSpeechRate(DEBIT);
        tts.setPitch(choisirVoix(locale) ? HAUTEUR_MASCULINE : HAUTEUR_A_DEFAUT);
        tts.setOnUtteranceProgressListener(
            new UtteranceProgressListener() {
                @Override
                public void onStart(String id) {}

                @Override
                public void onDone(String id) {
                    terminer();
                }

                @Override
                public void onError(String id) {
                    terminer();
                }
            }
        );
        pret = true;
        while (!attente.isEmpty()) enoncer(attente.poll());
    }

    // WHY: aucun moteur Android n'expose le genre d'une voix. Le nom est le seul indice —
    // « fr-fr-x-vlf#male_1-local » — et il faut une frontière de mot, sinon « female » est
    // reconnu comme masculin.
    private boolean choisirVoix(Locale locale) {
        Voice meilleure = null;
        int meilleurScore = Integer.MIN_VALUE;
        try {
            for (Voice voix : tts.getVoices()) {
                if (voix == null || voix.getLocale() == null) continue;
                if (!voix.getLocale().getLanguage().equals(locale.getLanguage())) continue;
                int score = noter(voix, locale);
                if (score > meilleurScore) {
                    meilleurScore = score;
                    meilleure = voix;
                }
            }
        } catch (Exception ignore) {
            return false;
        }
        if (meilleure == null) return false;
        tts.setVoice(meilleure);
        return estMasculine(meilleure.getName());
    }

    private int noter(Voice voix, Locale locale) {
        int score = 0;
        if (voix.getLocale().getCountry().equalsIgnoreCase(locale.getCountry())) score += 40;
        if (estMasculine(voix.getName())) score += 100;
        else if (contientMot(voix.getName(), "female")) score -= 100;
        score += voix.getQuality();
        if (voix.isNetworkConnectionRequired()) score -= 30;
        return score;
    }

    private boolean estMasculine(String nom) {
        return contientMot(nom, "male") && !contientMot(nom, "female");
    }

    private boolean contientMot(String nom, String indice) {
        if (nom == null) return false;
        return nom.toLowerCase(Locale.ROOT).matches(".*\\b" + indice + "\\b.*");
    }

    public void parler(String texte) {
        deposer(texte, false);
    }

    public void interrompreEtParler(String texte) {
        deposer(texte, true);
    }

    private void deposer(String texte, boolean prioritaire) {
        if (libere || texte == null || texte.trim().isEmpty()) return;
        if (prioritaire) {
            attente.clear();
            if (pret) tts.stop();
            enCours.set(0);
        }
        if (!pret) {
            attente.add(texte);
            return;
        }
        enoncer(texte);
    }

    // WHY: le moteur n'a pas de SSML. Le « | » des libellés découpe la phrase en énoncés
    // séparés par un silence — c'est ce qui donne le souffle du coach plutôt qu'un débit plat.
    private void enoncer(String texte) {
        String[] morceaux = texte.split(RESPIRATION);
        for (String morceau : morceaux) {
            String propre = morceau.trim();
            if (propre.isEmpty()) continue;
            prendreLeFocus();
            enCours.incrementAndGet();
            String id = "chiron-" + compteur.incrementAndGet();
            tts.speak(propre, TextToSpeech.QUEUE_ADD, new Bundle(), id);
            tts.playSilentUtterance(SILENCE_MS, TextToSpeech.QUEUE_ADD, id + "-silence");
        }
    }

    private void terminer() {
        if (enCours.decrementAndGet() > 0) return;
        enCours.set(0);
        rendreLeFocus();
    }

    public void taire() {
        attente.clear();
        if (pret) tts.stop();
        enCours.set(0);
        rendreLeFocus();
    }

    private void prendreLeFocus() {
        if (audioManager == null || demandeFocus != null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            demandeFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build();
            audioManager.requestAudioFocus(demandeFocus);
        } else {
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            );
        }
    }

    private void rendreLeFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (demandeFocus == null) return;
            audioManager.abandonAudioFocusRequest(demandeFocus);
            demandeFocus = null;
        } else {
            audioManager.abandonAudioFocus(focusListener);
        }
    }

    public void relacher() {
        libere = true;
        attente.clear();
        rendreLeFocus();
        if (tts == null) return;
        tts.stop();
        tts.shutdown();
        tts = null;
        pret = false;
    }
}
