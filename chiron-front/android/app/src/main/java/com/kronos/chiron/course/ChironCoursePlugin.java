package com.kronos.chiron.course;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "ChironCourse",
    permissions = {
        @Permission(
            alias = ChironCoursePlugin.LOCALISATION,
            strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }
        ),
        @Permission(alias = ChironCoursePlugin.MICRO, strings = { Manifest.permission.RECORD_AUDIO }),
        @Permission(
            alias = ChironCoursePlugin.NOTIFICATIONS,
            strings = { "android.permission.POST_NOTIFICATIONS" }
        )
    }
)
public class ChironCoursePlugin extends Plugin {

    static final String LOCALISATION = "localisation";
    static final String MICRO = "micro";
    static final String NOTIFICATIONS = "notifications";

    private Annonceur essai;
    private String langueDEssai;

    @Override
    public void load() {
        PontCourse.brancher((nom, donnees) -> notifyListeners(nom, enJsObject(donnees)));
    }

    @Override
    protected void handleOnDestroy() {
        PontCourse.debrancher();
        if (essai != null) essai.relacher();
        essai = null;
        super.handleOnDestroy();
    }

    @PluginMethod
    public void disponible(PluginCall appel) {
        JSObject reponse = new JSObject();
        reponse.put("natif", true);
        appel.resolve(reponse);
    }

    // WHY: les trois permissions se demandent d'un seul geste. Demander le micro au moment où
    // l'athlète appuie sur le bouton de son casque, en pleine course, ouvre une boîte de
    // dialogue qu'il ne verra jamais — l'écran est dans sa poche.
    @PluginMethod
    public void demanderPermissions(PluginCall appel) {
        if (toutAccorde()) {
            appel.resolve(etatDesPermissions());
            return;
        }
        requestAllPermissions(appel, "permissionsRendues");
    }

    @PermissionCallback
    private void permissionsRendues(PluginCall appel) {
        appel.resolve(etatDesPermissions());
    }

    @PluginMethod
    public void permissions(PluginCall appel) {
        appel.resolve(etatDesPermissions());
    }

    private boolean toutAccorde() {
        return (
            getPermissionState(LOCALISATION) == PermissionState.GRANTED &&
            getPermissionState(MICRO) == PermissionState.GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                getPermissionState(NOTIFICATIONS) == PermissionState.GRANTED)
        );
    }

    private JSObject etatDesPermissions() {
        JSObject reponse = new JSObject();
        reponse.put("localisation", accordee(Manifest.permission.ACCESS_FINE_LOCATION));
        reponse.put("micro", accordee(Manifest.permission.RECORD_AUDIO));
        reponse.put(
            "notifications",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            accordee("android.permission.POST_NOTIFICATIONS")
        );
        reponse.put("batterie", batterieExemptee());
        return reponse;
    }

    private boolean accordee(String permission) {
        return (
            ContextCompat.checkSelfPermission(getContext(), permission) ==
            PackageManager.PERMISSION_GRANTED
        );
    }

    @PluginMethod
    public void demarrer(PluginCall appel) {
        if (getPermissionState(LOCALISATION) != PermissionState.GRANTED) {
            appel.reject("localisation-refusee");
            return;
        }
        Intent intention = new Intent(getContext(), CourseService.class);
        intention.setAction(CourseService.ACTION_DEMARRER);
        intention.putExtra(CourseService.EXTRA_CONFIGURATION, appel.getData().toString());
        ContextCompat.startForegroundService(getContext(), intention);
        appel.resolve();
    }

    @PluginMethod
    public void arreter(PluginCall appel) {
        CourseService service = PontCourse.service();
        JSONObject reponse = service == null ? new JSONObject() : service.etat();
        try {
            reponse.put("points", service == null ? new JSONArray() : service.pointsJson());
        } catch (JSONException ignore) {}
        if (service != null) service.arreter();
        appel.resolve(enJsObject(reponse));
    }

    @PluginMethod
    public void configurer(PluginCall appel) {
        CourseService service = PontCourse.service();
        if (service == null) {
            appel.resolve();
            return;
        }
        service.configurer(appel.getData().toString());
        appel.resolve();
    }

    @PluginMethod
    public void basculerPause(PluginCall appel) {
        CourseService service = PontCourse.service();
        if (service == null) {
            appel.reject("course-absente");
            return;
        }
        service.basculerPause();
        appel.resolve(enJsObject(service.etat()));
    }

    @PluginMethod
    public void fixerCible(PluginCall appel) {
        CourseService service = PontCourse.service();
        if (service == null) {
            appel.reject("course-absente");
            return;
        }
        service.fixerCible(appel.getDouble("cibleMinParKm"));
        appel.resolve(enJsObject(service.etat()));
    }

    @PluginMethod
    public void annoncer(PluginCall appel) {
        CourseService service = PontCourse.service();
        if (service == null) {
            appel.resolve();
            return;
        }
        service.dire(appel.getString("texte", ""), Boolean.TRUE.equals(appel.getBoolean("prioritaire", false)));
        appel.resolve();
    }

    @PluginMethod
    public void executerAction(PluginCall appel) {
        CourseService service = PontCourse.service();
        if (service == null) {
            appel.reject("course-absente");
            return;
        }
        service.executerAction(appel.getString("action", ""));
        appel.resolve(enJsObject(service.etat()));
    }

    // WHY: le micro se demande ici, au moment de l'appui, et pas seulement au départ. Un refus
    // au lancement condamnait autrement le bouton pour toute la sortie, sans aucun moyen de
    // revenir dessus depuis l'application.
    @PluginMethod
    public void ecouter(PluginCall appel) {
        if (getPermissionState(MICRO) != PermissionState.GRANTED) {
            requestPermissionForAlias(MICRO, appel, "microRendu");
            return;
        }
        ouvrirLEcoute(appel);
    }

    @PermissionCallback
    private void microRendu(PluginCall appel) {
        if (getPermissionState(MICRO) != PermissionState.GRANTED) {
            appel.reject("permission");
            return;
        }
        ouvrirLEcoute(appel);
    }

    private void ouvrirLEcoute(PluginCall appel) {
        CourseService service = PontCourse.service();
        if (service == null) {
            appel.reject("course-absente");
            return;
        }
        service.ouvrirEcoute();
        appel.resolve();
    }

    // WHY: le curseur de volume vit dans les réglages, avant qu'aucun service ne tourne. Un
    // annonceur de courte vie appartenant au plugin est ce qui permet d'entendre le réglage au
    // moment où on le fait, plutôt que de le découvrir en pleine côte.
    @PluginMethod
    public void essayerVoix(PluginCall appel) {
        String texte = appel.getString("texte", "");
        String langue = appel.getString("langue", "fr");
        int volume = appel.getInt("volume", 100);
        CourseService service = PontCourse.service();
        if (service != null) {
            service.essayerVoix(texte, volume);
            appel.resolve();
            return;
        }
        if (essai == null || !langue.equals(langueDEssai)) {
            if (essai != null) essai.relacher();
            essai = new Annonceur(getContext(), langue);
            langueDEssai = langue;
        }
        essai.fixerVolume(volume);
        essai.interrompreEtParler(texte);
        appel.resolve();
    }

    @PluginMethod
    public void etat(PluginCall appel) {
        CourseService service = PontCourse.service();
        if (service == null) {
            JSObject reponse = new JSObject();
            reponse.put("demarree", false);
            reponse.put("microDisponible", SpeechRecognizer.isRecognitionAvailable(getContext()));
            appel.resolve(reponse);
            return;
        }
        appel.resolve(enJsObject(service.etat()));
    }

    @PluginMethod
    public void points(PluginCall appel) {
        CourseService service = PontCourse.service();
        JSONObject reponse = new JSONObject();
        try {
            reponse.put("points", service == null ? new JSONArray() : service.pointsJson());
        } catch (JSONException ignore) {}
        appel.resolve(enJsObject(reponse));
    }

    // WHY: un service au premier plan ne survit pas à l'optimisation de batterie agressive des
    // surcouches Xiaomi ou Huawei. L'exemption ne rend pas invulnérable, mais sans elle une
    // sortie longue est coupée en chemin.
    @PluginMethod
    public void exempterBatterie(PluginCall appel) {
        if (batterieExemptee()) {
            appel.resolve();
            return;
        }
        Intent intention = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intention.setData(Uri.parse("package:" + getContext().getPackageName()));
        intention.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intention);
            appel.resolve();
        } catch (Exception erreur) {
            appel.reject("indisponible");
        }
    }

    private static JSObject enJsObject(JSONObject source) {
        try {
            return JSObject.fromJSONObject(source);
        } catch (JSONException erreur) {
            return new JSObject();
        }
    }

    private boolean batterieExemptee() {
        PowerManager gestionnaire = (PowerManager) getContext()
            .getSystemService(android.content.Context.POWER_SERVICE);
        if (gestionnaire == null) return true;
        return gestionnaire.isIgnoringBatteryOptimizations(getContext().getPackageName());
    }
}
