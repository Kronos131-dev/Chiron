package com.kronos.chiron.voix;

import android.Manifest;
import android.speech.SpeechRecognizer;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.kronos.chiron.course.Ecoute;

// WHY: webkitSpeechRecognition n'existe pas dans la WebView Android. Sans ce pont, le micro du
// Chat — la façon principale de logger une série à la voix — est mort dans l'application
// installée, alors qu'il fonctionne dans le navigateur.
@CapacitorPlugin(
    name = "ChironVoix",
    permissions = {
        @Permission(alias = ChironVoixPlugin.MICRO, strings = { Manifest.permission.RECORD_AUDIO })
    }
)
public class ChironVoixPlugin extends Plugin {

    static final String MICRO = "micro";

    private Ecoute ecoute;

    @PluginMethod
    public void disponible(PluginCall appel) {
        JSObject reponse = new JSObject();
        reponse.put("disponible", SpeechRecognizer.isRecognitionAvailable(getContext()));
        appel.resolve(reponse);
    }

    @PluginMethod
    public void demarrer(PluginCall appel) {
        if (getPermissionState(MICRO) != PermissionState.GRANTED) {
            requestPermissionForAlias(MICRO, appel, "microRendu");
            return;
        }
        ouvrir(appel);
    }

    @PermissionCallback
    private void microRendu(PluginCall appel) {
        if (getPermissionState(MICRO) != PermissionState.GRANTED) {
            appel.reject("micro-refuse");
            return;
        }
        ouvrir(appel);
    }

    private void ouvrir(PluginCall appel) {
        arreterEcoute();
        ecoute = new Ecoute(
            getContext(),
            appel.getString("langue", "fr"),
            new Ecoute.Ecouteur() {
                @Override
                public void transcription(String texte, boolean definitif) {
                    JSObject donnees = new JSObject();
                    donnees.put("texte", texte);
                    donnees.put("definitif", definitif);
                    notifyListeners(definitif ? "final" : "partiel", donnees);
                }

                @Override
                public void echec(String raison) {
                    JSObject donnees = new JSObject();
                    donnees.put("raison", raison);
                    notifyListeners("erreur", donnees);
                }
            }
        );
        ecoute.demarrer();
        appel.resolve();
    }

    @PluginMethod
    public void arreter(PluginCall appel) {
        arreterEcoute();
        appel.resolve();
    }

    private void arreterEcoute() {
        if (ecoute == null) return;
        ecoute.arreter();
        ecoute = null;
    }

    @Override
    protected void handleOnDestroy() {
        arreterEcoute();
        super.handleOnDestroy();
    }
}
