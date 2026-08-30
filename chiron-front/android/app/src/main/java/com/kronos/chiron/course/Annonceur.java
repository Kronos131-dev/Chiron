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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class Annonceur {

    private static final float HAUTEUR_MASCULINE = 0.85f;
    private static final float HAUTEUR_A_DEFAUT = 0.72f;
    private static final float DEBIT = 0.92f;
    private static final String RESPIRATION = "\\|";
    private static final long SILENCE_MS = 180;
    private static final int POIDS_GENRE = 10000;
    private static final int POIDS_HORS_LIGNE = 1000;
    private static final int POIDS_PAYS = 500;

    // WHY: aucun moteur Android n'expose le genre d'une voix, et les voix françaises de Google
    // portent un identifiant opaque — « fr-fr-x-frd-local » ne dit rien de ce qu'on entendra.
    // Le choix automatique restait donc un pari, et il tombait sur une voix féminine. Celle-ci
    // a été retenue à l'oreille. Absente de l'appareil, on repasse au score.
    private static final Map<String, String> VOIX_PREFEREE = Map.of("fr", "fr-fr-x-frd-local");

    private final Context context;
    private final AudioManager audioManager;
    private final Deque<String> attente = new ArrayDeque<>();
    private final AtomicInteger enCours = new AtomicInteger(0);
    private final AtomicInteger compteur = new AtomicInteger(0);

    // WHY: le guetteur et le moteur de synthèse se disputent le micro. Sans ce témoin, Chiron
    // s'entend parler et cherche un ordre dans ses propres annonces — quand le moteur ne rend pas
    // simplement ERROR_RECOGNIZER_BUSY, ce qui tue l'écoute pour le reste de la sortie.
    public interface Temoin {
        void parole(boolean enCours);
    }

    private volatile Temoin temoin;

    private TextToSpeech tts;
    private volatile boolean pret = false;
    private boolean libere = false;
    private int volumePourcent = 100;
    private Runnable apresSilence;
    private Locale locale = Locale.FRANCE;
    private volatile long derniereParoleA = 0;
    private AudioFocusRequest demandeFocus;
    private final AudioManager.OnAudioFocusChangeListener focusListener = changement -> {};

    public Annonceur(Context context, String langue) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.tts = new TextToSpeech(context, statut -> preparer(statut, langue));
    }

    private void preparer(int statut, String langue) {
        if (libere || statut != TextToSpeech.SUCCESS) return;
        locale = "en".equals(langue) ? Locale.US : Locale.FRANCE;
        // WHY: setLanguage échoue en silence quand les données de la langue ne sont pas
        // installées, et speak() ne rend alors plus un son — un coach muet pour toute la
        // sortie. La langue générique puis la langue du système sont les deux replis.
        int retour = tts.setLanguage(locale);
        if (
            retour == TextToSpeech.LANG_MISSING_DATA ||
            retour == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            retour = tts.setLanguage(new Locale(locale.getLanguage()));
        }
        if (
            retour == TextToSpeech.LANG_MISSING_DATA ||
            retour == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts.setLanguage(Locale.getDefault());
        }
        // WHY: USAGE_MEDIA et non USAGE_ASSISTANCE_NAVIGATION_GUIDANCE. Le guidage est un
        // canal à part, que plusieurs surcouches atténuent fortement et que certains casques
        // Bluetooth n'ouvrent pas du tout écran verrouillé — c'est la voix faible puis muette
        // constatée sur le terrain. Le canal média est celui où la musique s'entend déjà,
        // donc celui où le coach s'entend forcément ; l'atténuation de la musique est alors
        // obtenue par la demande de focus, pas par l'attribut.
        tts.setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        );
        tts.setSpeechRate(DEBIT);
        appliquerLaVoix();
        tts.setOnUtteranceProgressListener(
            new UtteranceProgressListener() {
                @Override
                public void onStart(String id) {
                    derniereParoleA = System.currentTimeMillis();
                    signaler(true);
                }

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


    private void appliquerLaVoix() {
        String souhaitee = VOIX_PREFEREE.get(locale.getLanguage());
        Voice retenue = souhaitee == null ? null : parNom(souhaitee);
        if (retenue != null) {
            tts.setVoice(retenue);
            tts.setPitch(estMasculine(retenue.getName()) ? HAUTEUR_MASCULINE : HAUTEUR_A_DEFAUT);
            return;
        }
        tts.setPitch(choisirVoix(locale) ? HAUTEUR_MASCULINE : HAUTEUR_A_DEFAUT);
    }

    private Voice parNom(String nom) {
        try {
            for (Voice voix : tts.getVoices()) {
                if (voix != null && nom.equalsIgnoreCase(voix.getName())) return voix;
            }
        } catch (Exception ignore) {}
        return null;
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

    // WHY: une voix réseau se tait dès que le signal tombe — c'est-à-dire en forêt, en montée,
    // exactement là où l'athlète attend le coach. Elle n'est retenue que si l'appareil n'a
    // strictement rien d'autre dans la langue demandée.
    private int noter(Voice voix, Locale locale) {
        int score = 0;
        if (voix.getLocale().getCountry().equalsIgnoreCase(locale.getCountry())) score += POIDS_PAYS;
        if (estMasculine(voix.getName())) score += POIDS_GENRE;
        else if (contientMot(voix.getName(), "female")) score -= POIDS_GENRE;
        if (voix.isNetworkConnectionRequired()) score -= POIDS_HORS_LIGNE;
        score += voix.getQuality();
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
        deposer(texte, false, null);
    }

    public void interrompreEtParler(String texte) {
        deposer(texte, true, null);
    }

    // WHY: le pourcentage est celui du volume média maximal de l'appareil, pas une atténuation.
    // Le paramètre de volume du moteur ne sait que descendre sous le niveau déjà réglé sur le
    // téléphone, et c'est ce niveau que l'athlète trouve trop bas : monter au-dessus n'est
    // possible qu'en poussant le flux média lui-même, rendu tel qu'il était dès la phrase finie.
    public boolean pret() {
        return pret;
    }

    // WHY: horodaté sur onStart, pas sur speak(). C'est la seule preuve que le moteur a
    // réellement ouvert la bouche : une file qui s'empile sans jamais sortir un son est
    // exactement le symptôme rapporté écran verrouillé, et speak() ne le distingue pas.
    public long derniereParoleA() {
        return derniereParoleA;
    }

    public void fixerVolume(int pourcentage) {
        volumePourcent = Math.min(100, Math.max(0, pourcentage));
    }

    public void parlerPuis(String texte, Runnable suite) {
        deposer(texte, true, suite);
    }

    // WHY: la suite appartient à l'énoncé qui la porte. La laisser en place quand une autre
    // phrase la remplace la ferait courir à la fin de celle-là — c'est-à-dire ouvrir le micro
    // à l'arrivée, sur le « C'est fini » qui coupe l'annonce d'écoute.
    private void deposer(String texte, boolean prioritaire, Runnable suite) {
        if (libere || texte == null || texte.trim().isEmpty()) return;
        if (prioritaire) {
            attente.clear();
            if (pret) tts.stop();
            enCours.set(0);
        }
        apresSilence = suite;
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
            Bundle parametres = new Bundle();
            parametres.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumePourcent / 100f);
            tts.speak(propre, TextToSpeech.QUEUE_ADD, parametres, id);
            tts.playSilentUtterance(SILENCE_MS, TextToSpeech.QUEUE_ADD, id + "-silence");
        }
    }

    private void terminer() {
        if (enCours.decrementAndGet() > 0) return;
        enCours.set(0);
        signaler(false);
        rendreLeFocus();
        Runnable suite = apresSilence;
        apresSilence = null;
        if (suite != null) suite.run();
    }

    public void taire() {
        attente.clear();
        apresSilence = null;
        if (pret) tts.stop();
        enCours.set(0);
        signaler(false);
        rendreLeFocus();
    }

    public void fixerTemoin(Temoin temoin) {
        this.temoin = temoin;
    }

    private void signaler(boolean parle) {
        Temoin ecouteur = temoin;
        if (ecouteur != null) ecouteur.parole(parle);
    }

    private void prendreLeFocus() {
        if (audioManager == null || demandeFocus != null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            demandeFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
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
        apresSilence = null;
        rendreLeFocus();
        if (tts == null) return;
        tts.stop();
        tts.shutdown();
        tts = null;
        pret = false;
    }
}
