package com.kronos.chiron.course;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;
import androidx.media.session.MediaButtonReceiver;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public final class TelecommandeCasque {

    public interface Ecouteur {
        void action(String action);
    }

    private static final long SEUIL_APPUI_LONG_MS = 600;

    private final MediaSessionCompat session;
    private final Ecouteur ecouteur;
    private final Map<String, String> appuiCourt = new HashMap<>();
    private final Map<String, String> appuiLong = new HashMap<>();

    private long debutAppuiMs = 0;
    private String boutonAppuye = null;
    private boolean longDejaDeclenche = false;

    public TelecommandeCasque(Context context, Ecouteur ecouteur) {
        this.ecouteur = ecouteur;
        this.session = new MediaSessionCompat(
            context,
            "chiron-course",
            new ComponentName(context, MediaButtonReceiver.class),
            null
        );
        this.session.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            );
        this.session.setCallback(
                new MediaSessionCompat.Callback() {
                    @Override
                    public boolean onMediaButtonEvent(Intent intent) {
                        return traiter(intent);
                    }
                }
            );
        this.session.setActive(true);
    }

    public void configurer(JSONObject court, JSONObject longue) {
        remplir(appuiCourt, court);
        remplir(appuiLong, longue);
    }

    private void remplir(Map<String, String> cible, JSONObject source) {
        cible.clear();
        if (source == null) return;
        java.util.Iterator<String> cles = source.keys();
        while (cles.hasNext()) {
            String cle = cles.next();
            cible.put(cle, source.optString(cle, "rien"));
        }
    }

    // WHY: c'est le seul endroit d'Android qui livre le KeyEvent brut d'un bouton de casque,
    // avec son ACTION_DOWN et son ACTION_UP. mediaSession côté web ne transmettait qu'une
    // impulsion sémantique : ni durée, ni relâchement, donc pas d'appui long possible.
    public MediaSessionCompat session() {
        return session;
    }

    private boolean traiter(Intent intent) {
        KeyEvent evenement = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (evenement == null) return false;
        String bouton = nommer(evenement.getKeyCode());
        if (bouton == null) return false;

        if (evenement.getAction() == KeyEvent.ACTION_DOWN) {
            if (evenement.getRepeatCount() == 0) {
                debutAppuiMs = SystemClock.uptimeMillis();
                boutonAppuye = bouton;
                longDejaDeclenche = false;
                return true;
            }
            if (!longDejaDeclenche && duree(evenement) >= SEUIL_APPUI_LONG_MS) {
                longDejaDeclenche = true;
                declencher(appuiLong.get(bouton));
            }
            return true;
        }

        if (evenement.getAction() != KeyEvent.ACTION_UP) return false;
        if (!bouton.equals(boutonAppuye)) return false;
        boutonAppuye = null;
        if (longDejaDeclenche) return true;
        boolean longue = duree(evenement) >= SEUIL_APPUI_LONG_MS;
        declencher(longue ? appuiLong.get(bouton) : appuiCourt.get(bouton));
        return true;
    }

    private long duree(KeyEvent evenement) {
        long depuisEvenement = evenement.getEventTime() - evenement.getDownTime();
        if (depuisEvenement > 0) return depuisEvenement;
        return SystemClock.uptimeMillis() - debutAppuiMs;
    }

    private void declencher(String action) {
        if (action == null || action.isEmpty() || "rien".equals(action)) return;
        ecouteur.action(action);
    }

    private String nommer(int codeTouche) {
        switch (codeTouche) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return "play";
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return "nexttrack";
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return "previoustrack";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return "seekforward";
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return "seekbackward";
            default:
                return null;
        }
    }

    public void annoncerEnCours(String titre, boolean enLecture) {
        session.setMetadata(
            new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titre)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Chiron")
                .build()
        );
        session.setPlaybackState(
            new PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY |
                    PlaybackStateCompat.ACTION_PAUSE |
                    PlaybackStateCompat.ACTION_PLAY_PAUSE |
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                    PlaybackStateCompat.ACTION_FAST_FORWARD |
                    PlaybackStateCompat.ACTION_REWIND
                )
                .setState(
                    enLecture ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        );
    }

    public void relacher() {
        session.setActive(false);
        session.release();
    }
}
