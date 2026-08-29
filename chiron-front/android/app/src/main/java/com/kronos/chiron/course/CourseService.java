package com.kronos.chiron.course;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.media.session.MediaButtonReceiver;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.kronos.chiron.MainActivity;
import com.kronos.chiron.R;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class CourseService extends Service {

    public static final String ACTION_DEMARRER = "com.kronos.chiron.course.DEMARRER";
    public static final String ACTION_BASCULER_PAUSE = "com.kronos.chiron.course.BASCULER_PAUSE";
    public static final String ACTION_ECOUTER = "com.kronos.chiron.course.ECOUTER";
    public static final String ACTION_ARRETER = "com.kronos.chiron.course.ARRETER";
    public static final String EXTRA_CONFIGURATION = "configuration";

    private static final String CANAL = "chiron.course";
    private static final int NOTIFICATION = 4201;
    private static final long TICK_MS = 1000;
    private static final long INTERVALLE_GPS_MS = 1000;
    private static final long SILENCE_GPS_MS = 20000;
    private static final double SECONDES_PAR_MINUTE = 60;
    private static final double MS_PAR_SECONDE = 1000;
    private static final double CIBLE_MIN = 2.5;
    private static final double CIBLE_MAX = 15;
    private static final double PAS_CIBLE_MIN_PAR_KM = 5 / SECONDES_PAR_MINUTE;
    private static final double ECART_TOLERE_MIN_PAR_KM = 0.25;
    private static final double ECART_FRANC_MIN_PAR_KM = 0.6;
    private static final long DUREE_ECART_MS = 15000;
    private static final long SILENCE_ENTRE_ANNONCES_MS = 60000;
    private static final long DUREE_VEILLE_MS = 6 * 60 * 60 * 1000L;
    private static final long DELAI_MAX_AVANT_ECOUTE_MS = 2500;

    private final Handler boucle = new Handler(Looper.getMainLooper());
    private final Mesure mesure = new Mesure();
    private final Phrases phrases = new Phrases();

    private Annonceur annonceur;
    private TelecommandeCasque telecommande;
    private Ecoute ecoute;
    private FusedLocationProviderClient gps;
    private LocationCallback rappelGps;
    private PowerManager.WakeLock veille;

    private boolean demarree = false;
    private boolean enPause = false;
    private boolean ouvrirUneCoupure = false;
    private long msAccumules = 0;
    private Long repriseA = null;
    private Double cibleMinParKm = null;
    private int kmAnnonces = 0;
    private Long ecartDepuis = null;
    private long derniereAnnonceEcart = 0;
    private long derniereReception = 0;
    private Integer precisionM = null;
    private int pointsPublies = 0;
    private String erreurGps = null;
    private String titre = "Course";
    private int volumeVoix = 100;
    private double objectifM = 0;
    private boolean objectifAnnonce = false;
    private long demandeEcouteA = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PontCourse.enregistrer(this);
        creerLeCanal();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        // WHY: les touches du casque arrivent par ce chemin quand l'application n'est plus au
        // premier plan. MediaButtonReceiver les relaie au service, qui les rend a la session.
        if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
            if (telecommande != null) {
                MediaButtonReceiver.handleIntent(telecommande.session(), intent);
            }
            return START_STICKY;
        }
        if (ACTION_DEMARRER.equals(action)) {
            demarrer(intent.getStringExtra(EXTRA_CONFIGURATION));
        } else if (ACTION_BASCULER_PAUSE.equals(action)) {
            basculerPause();
        } else if (ACTION_ECOUTER.equals(action)) {
            executerAction("ecouter");
        } else if (ACTION_ARRETER.equals(action)) {
            arreter();
        } else if (!demarree) {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        PontCourse.oublier(this);
        boucle.removeCallbacksAndMessages(null);
        couperGps();
        if (ecoute != null) ecoute.arreter();
        if (telecommande != null) telecommande.relacher();
        if (annonceur != null) annonceur.relacher();
        relacherLaVeille();
        super.onDestroy();
    }

    private void demarrer(String configuration) {
        JSONObject config = lireJson(configuration);
        phrases.charger(config.optJSONObject("phrases"));
        phrases.fixerUnite(config.optString("uniteAllure", "minParKm"));
        titre = config.optString("titre", "Course");
        String langue = config.optString("langue", "fr");
        volumeVoix = config.optInt("volumeVoix", volumeVoix);
        objectifM = config.optDouble("objectifDistanceM", 0);
        mesure.fixerObjectif(objectifM);
        if (config.has("cibleMinParKm") && !config.isNull("cibleMinParKm")) {
            cibleMinParKm = config.optDouble("cibleMinParKm");
        }

        seMettreAuPremierPlan();
        tenirLaVeille();

        annonceur = new Annonceur(this, langue);
        annonceur.fixerVolume(volumeVoix);
        annonceur.fixerVoix(config.optString("voix", null));
        ecoute = new Ecoute(this, langue, new EcouteurDeVoix());
        telecommande = new TelecommandeCasque(this, this::executerAction);
        telecommande.configurer(
            config.optJSONObject("appuiCourt"),
            config.optJSONObject("appuiLong")
        );
        telecommande.annoncerEnCours(titre, true);

        demarree = true;
        enPause = false;
        repriseA = System.currentTimeMillis();
        derniereReception = repriseA;
        ecouterGps();
        boucle.postDelayed(this::tick, TICK_MS);
        annonceur.parler(phrases.t("started"));
        publierEtat();
    }

    public void configurer(String configuration) {
        JSONObject config = lireJson(configuration);
        if (config.has("phrases")) phrases.charger(config.optJSONObject("phrases"));
        if (config.has("uniteAllure")) phrases.fixerUnite(config.optString("uniteAllure"));
        if (config.has("volumeVoix")) {
            volumeVoix = config.optInt("volumeVoix", volumeVoix);
        objectifM = config.optDouble("objectifDistanceM", 0);
        mesure.fixerObjectif(objectifM);
            if (annonceur != null) annonceur.fixerVolume(volumeVoix);
        }
        if (config.has("voix") && annonceur != null) {
            annonceur.fixerVoix(config.optString("voix", null));
        }
        titre = config.optString("titre", titre);
        if (telecommande != null) {
            telecommande.configurer(
                config.optJSONObject("appuiCourt"),
                config.optJSONObject("appuiLong")
            );
        }
        rafraichirLaNotification();
    }

    public void arreter() {
        if (!demarree) {
            stopSelf();
            return;
        }
        accumulerLeTemps();
        demarree = false;
        couperGps();
        if (ecoute != null) ecoute.arreter();
        if (annonceur != null) annonceur.interrompreEtParler(phrases.t("finished"));
        boucle.removeCallbacksAndMessages(null);
        publierEtat();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        boucle.postDelayed(this::stopSelf, 4000);
    }

    public void basculerPause() {
        if (!demarree) return;
        if (enPause) {
            repriseA = System.currentTimeMillis();
            derniereReception = repriseA;
            enPause = false;
            // WHY: le premier point capté après une reprise est à une distance arbitraire du
            // dernier point d'avant la pause. La coupure est ce qui empêche un trajet en
            // voiture de s'ajouter à la sortie — ici comme dans le tracker web et le serveur.
            ouvrirUneCoupure = true;
            ecouterGps();
            dire(phrases.t("resumed"), false);
        } else {
            accumulerLeTemps();
            enPause = true;
            couperGps();
            dire(phrases.t("paused"), false);
        }
        if (telecommande != null) telecommande.annoncerEnCours(titre, !enPause);
        publierEtat();
        rafraichirLaNotification();
    }

    public void fixerCible(Double minParKm) {
        if (minParKm == null) {
            cibleMinParKm = null;
            ecartDepuis = null;
            publierEtat();
            return;
        }
        double bornee = Math.min(CIBLE_MAX, Math.max(CIBLE_MIN, minParKm));
        cibleMinParKm = bornee;
        ecartDepuis = null;
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("cible", phrases.allureParlee(Phrases.minParKmVersKmh(bornee)));
        dire(phrases.t("target", valeurs), true);
        publierEtat();
    }

    public void essayerVoix(String texte, int volume, String voix) {
        volumeVoix = volume;
        if (annonceur == null) return;
        annonceur.fixerVolume(volume);
        annonceur.fixerVoix(voix);
        annonceur.interrompreEtParler(texte);
    }

    public Annonceur annonceur() {
        return annonceur;
    }

    public void dire(String texte, boolean prioritaire) {
        if (annonceur == null) return;
        if (prioritaire) annonceur.interrompreEtParler(texte);
        else annonceur.parler(texte);
    }

    // WHY: le moteur de reconnaissance et le moteur de synthèse se disputent le micro. Ouvrir
    // l'écoute pendant que « J'écoute » se prononce faisait entendre à Chiron sa propre voix,
    // ou renvoyait ERROR_RECOGNIZER_BUSY : l'écoute part quand la phrase est finie, et le délai
    // de garde couvre le cas où le moteur ne rend jamais la main.
    public void ouvrirEcoute() {
        if (ecoute == null) {
            PontCourse.publier("echecEcoute", raison("indisponible"));
            return;
        }
        if (ecoute.active()) {
            ecoute.cloturer();
            return;
        }
        if (!ecoute.disponible()) {
            PontCourse.publier("echecEcoute", raison(ecoute.raisonIndisponible()));
            return;
        }
        if (annonceur == null) {
            ecoute.demarrer();
            publierEtat();
            return;
        }
        // WHY: la fin de la phrase et le délai de garde mènent au même appel, et le garde peut
        // arriver après que l'écoute se soit déjà ouverte puis refermée. Le jeton est ce qui
        // empêche le retardataire de rouvrir le micro tout seul dans la poche de l'athlète.
        demandeEcouteA = System.currentTimeMillis();
        final long jeton = demandeEcouteA;
        annonceur.parlerPuis(phrases.t("listening"), () -> lancerLEcoute(jeton));
        boucle.postDelayed(() -> lancerLEcoute(jeton), DELAI_MAX_AVANT_ECOUTE_MS);
    }

    private void lancerLEcoute(long jeton) {
        if (ecoute == null || jeton != demandeEcouteA) return;
        demandeEcouteA = 0;
        ecoute.demarrer();
        publierEtat();
    }

    private JSONObject raison(String valeur) {
        JSONObject donnees = new JSONObject();
        try {
            donnees.put("raison", valeur);
        } catch (JSONException ignore) {}
        return donnees;
    }

    // WHY: ces actions doivent aboutir écran verrouillé, quand la WebView peut être bridée.
    // Elles sont donc exécutées ici, et seulement notifiées au JS pour que l'écran suive.
    public void executerAction(String action) {
        if (action == null) return;
        switch (action) {
            case "ecouter":
                ouvrirEcoute();
                break;
            case "pause":
                basculerPause();
                break;
            case "plusVite":
                deplacerCible(-PAS_CIBLE_MIN_PAR_KM);
                break;
            case "moinsVite":
                deplacerCible(PAS_CIBLE_MIN_PAR_KM);
                break;
            case "allure":
                dire(phrases.allureParlee(mesure.allureCouranteKmh()), true);
                break;
            case "distance":
                dire(avecDistance("distance"), true);
                break;
            case "duree":
                dire(phrases.dureeParlee(dureeS()), true);
                break;
            case "bilan":
                dire(bilan(), true);
                break;
            default:
                return;
        }
        JSONObject donnees = new JSONObject();
        try {
            donnees.put("action", action);
        } catch (JSONException ignore) {}
        PontCourse.publier("casque", donnees);
        publierEtat();
    }

    private void deplacerCible(double delta) {
        double base = cibleMinParKm == null ? 6 : cibleMinParKm;
        fixerCible(base + delta);
    }

    private String avecDistance(String cle) {
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("km", Phrases.formaterDistance(mesure.distanceM()));
        return phrases.t(cle, valeurs);
    }

    private String bilan() {
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("km", Phrases.formaterDistance(mesure.distanceM()));
        valeurs.put("temps", phrases.dureeParlee(dureeS()));
        valeurs.put("allure", phrases.allureParlee(mesure.allureCouranteKmh()));
        return phrases.t("summary", valeurs);
    }

    private void tick() {
        if (!demarree) return;
        boucle.postDelayed(this::tick, TICK_MS);
        publierEtat();
        rafraichirLaNotification();
        if (enPause) return;
        annoncerLObjectif();
        annoncerKilometres();
        surveillerAllure();
    }

    // WHY: l'objectif est dit une fois et la course ne s'arrête pas. L'athlète a demandé dix
    // kilomètres, il veut savoir en combien il les a bouclés — et rester libre d'en courir deux
    // de plus sans que l'application décide à sa place que c'est fini.
    private void annoncerLObjectif() {
        if (objectifAnnonce || mesure.instantObjectifMs() == null) return;
        objectifAnnonce = true;
        long dureeObjectifS = (long) Math.floor(mesure.instantObjectifMs() / MS_PAR_SECONDE);
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("km", Phrases.formaterDistance(objectifM));
        valeurs.put("temps", phrases.dureeParlee(dureeObjectifS));
        dire(phrases.t("goalReached", valeurs), true);
        publierEtat();
    }

    // WHY: seul le dernier kilomètre franchi est annoncé. Après une reprise de signal
    // plusieurs peuvent tomber d'un tick au suivant, et les enchaîner couvrirait le kilomètre
    // en cours sans rien apprendre.
    private void annoncerKilometres() {
        int franchis = mesure.kilometresFranchis();
        if (franchis <= kmAnnonces) return;
        kmAnnonces = franchis;
        // WHY: c'est l'allure du kilomètre qui vient d'être bouclé qui apprend quelque chose,
        // pas la moyenne depuis le départ — celle-ci se lisse et cesse de réagir au bout d'une
        // demi-heure, alors que l'athlète veut savoir s'il tient ou s'il s'écroule.
        double allureDuKm = mesure.allureDuKilometreKmh(franchis);
        long dureeDuKmS = (long) Math.floor(mesure.dureeDuKilometreMs(franchis) / MS_PAR_SECONDE);
        JSONObject franchissement = new JSONObject();
        try {
            franchissement.put("kilometre", franchis);
            franchissement.put("dureeSplitS", dureeDuKmS);
            franchissement.put("allureSplitKmh", allureDuKm);
        } catch (JSONException ignore) {}
        PontCourse.publier("kilometre", franchissement);
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("km", String.valueOf(franchis));
        valeurs.put("temps", phrases.dureeParlee(dureeS()));
        valeurs.put("allure", phrases.allureParlee(allureDuKm));
        dire(phrases.t("km", valeurs), false);
    }

    // WHY: l'écart doit tenir quinze secondes avant d'être dit, sinon une côte ou un pont
    // déclenche une annonce à chaque tick ; et le coach se tait une minute après avoir parlé,
    // faute de quoi l'athlète coupe le son et n'entend plus rien du tout.
    private void surveillerAllure() {
        Double ecart = ecartAllure();
        long maintenant = System.currentTimeMillis();
        if (ecart == null || Math.abs(ecart) <= ECART_TOLERE_MIN_PAR_KM) {
            ecartDepuis = null;
            return;
        }
        if (ecartDepuis == null) {
            ecartDepuis = maintenant;
            return;
        }
        if (maintenant - ecartDepuis < DUREE_ECART_MS) return;
        if (maintenant - derniereAnnonceEcart < SILENCE_ENTRE_ANNONCES_MS) return;

        derniereAnnonceEcart = maintenant;
        ecartDepuis = null;
        boolean franc = Math.abs(ecart) >= ECART_FRANC_MIN_PAR_KM;
        String cle = ecart > 0
            ? (franc ? "speedUp" : "speedUpABit")
            : (franc ? "slowDown" : "slowDownABit");
        dire(phrases.t(cle), false);
    }

    private Double ecartAllure() {
        if (cibleMinParKm == null) return null;
        Double courante = Phrases.minParKm(mesure.allureCouranteKmh());
        if (courante == null) return null;
        return courante - cibleMinParKm;
    }

    private void accumulerLeTemps() {
        if (repriseA == null) return;
        msAccumules += Math.max(0, System.currentTimeMillis() - repriseA);
        repriseA = null;
    }

    public long dureeMs() {
        if (repriseA == null) return msAccumules;
        return msAccumules + Math.max(0, System.currentTimeMillis() - repriseA);
    }

    public long dureeS() {
        return (long) Math.floor(dureeMs() / MS_PAR_SECONDE);
    }

    private void ecouterGps() {
        if (rappelGps != null) return;
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            erreurGps = "permission";
            return;
        }
        if (gps == null) gps = LocationServices.getFusedLocationProviderClient(this);
        rappelGps = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult resultat) {
                Location position = resultat.getLastLocation();
                if (position != null) accepterPosition(position);
            }
        };
        LocationRequest demande = new LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVALLE_GPS_MS
        )
            .setMinUpdateIntervalMillis(INTERVALLE_GPS_MS)
            .setMinUpdateDistanceMeters(0)
            .setWaitForAccurateLocation(false)
            .build();
        try {
            gps.requestLocationUpdates(demande, rappelGps, Looper.getMainLooper());
            erreurGps = null;
        } catch (SecurityException erreur) {
            erreurGps = "permission";
            rappelGps = null;
        }
    }

    private void couperGps() {
        if (gps == null || rappelGps == null) return;
        gps.removeLocationUpdates(rappelGps);
        rappelGps = null;
    }

    // WHY: sans ces deux filtres la dérive GPS à l'arrêt ajoute des centaines de mètres — un
    // feu rouge de deux minutes suffit à inventer 200 m.
    private void accepterPosition(Location position) {
        if (!demarree || enPause) return;
        derniereReception = System.currentTimeMillis();
        precisionM = Math.round(position.getAccuracy());
        erreurGps = null;
        if (position.getAccuracy() > Mesure.PRECISION_MAX_M) return;

        Point point = new Point(
            Mesure.arrondirDegres(position.getLatitude()),
            Mesure.arrondirDegres(position.getLongitude()),
            position.getTime() > 0 ? position.getTime() : System.currentTimeMillis(),
            position.hasAltitude() ? position.getAltitude() : null,
            ouvrirUneCoupure && mesure.nbPoints() > 0
        );

        if (ouvrirUneCoupure) ouvrirUneCoupure = false;
        else if (mesure.tropProche(point)) return;

        mesure.ajouter(point);
    }

    public JSONObject etat() {
        JSONObject etat = new JSONObject();
        try {
            etat.put("demarree", demarree);
            etat.put("enPause", enPause);
            etat.put("distanceM", mesure.distanceM());
            etat.put("dureeMs", dureeMs());
            etat.put("allureCouranteKmh", mesure.allureCouranteKmh());
            etat.put("allureMoyenneKmh", mesure.allureMoyenneKmh(dureeMs()));
            etat.put("nbPoints", mesure.nbPoints());
            etat.put("kilometres", mesure.kilometresFranchis());
            etat.put("precisionM", precisionM == null ? JSONObject.NULL : precisionM);
            etat.put("erreurGps", erreurGps == null ? JSONObject.NULL : erreurGps);
            etat.put(
                "signalPerdu",
                demarree &&
                !enPause &&
                derniereReception > 0 &&
                System.currentTimeMillis() - derniereReception > SILENCE_GPS_MS
            );
            etat.put("ecoute", ecoute != null && ecoute.active());
            etat.put("microDisponible", ecoute == null || ecoute.moteurPresent());
            etat.put("objectifDistanceM", objectifM);
            etat.put(
                "objectifDureeMs",
                mesure.instantObjectifMs() == null ? 0 : mesure.instantObjectifMs()
            );
            etat.put("voixPrete", annonceur != null && annonceur.pret());
            etat.put("derniereParoleA", annonceur == null ? 0 : annonceur.derniereParoleA());
            etat.put(
                "cibleMinParKm",
                cibleMinParKm == null ? JSONObject.NULL : (double) cibleMinParKm
            );
        } catch (JSONException ignore) {}
        return etat;
    }

    public JSONArray pointsJson() {
        JSONArray tableau = new JSONArray();
        List<Point> points = mesure.points();
        for (Point point : points) {
            try {
                tableau.put(point.enJson());
            } catch (JSONException ignore) {}
        }
        return tableau;
    }

    // WHY: les points ne sont publiés qu'une fois chacun. Renvoyer la trace entière à chaque
    // seconde ferait passer plusieurs centaines de kilo-octets par la passerelle sur une sortie
    // d'une heure, pour une carte que personne ne regarde écran verrouillé.
    private void publierEtat() {
        JSONObject etat = etat();
        try {
            etat.put("nouveauxPoints", pointsDepuis(pointsPublies));
        } catch (JSONException ignore) {}
        pointsPublies = mesure.nbPoints();
        PontCourse.publier("etat", etat);
    }

    private JSONArray pointsDepuis(int depuis) {
        JSONArray tableau = new JSONArray();
        List<Point> points = mesure.points();
        for (int i = Math.max(0, depuis); i < points.size(); i++) {
            try {
                tableau.put(points.get(i).enJson());
            } catch (JSONException ignore) {}
        }
        return tableau;
    }

    private void creerLeCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager gestionnaire = getSystemService(NotificationManager.class);
        if (gestionnaire == null || gestionnaire.getNotificationChannel(CANAL) != null) return;
        NotificationChannel canal = new NotificationChannel(
            CANAL,
            "Course",
            NotificationManager.IMPORTANCE_LOW
        );
        canal.setShowBadge(false);
        canal.setSound(null, null);
        gestionnaire.createNotificationChannel(canal);
    }

    // WHY: le type location autorise le GPS écran verrouillé et le type microphone la
    // reconnaissance vocale. Depuis Android 14 déclarer un type dont la permission est refusée
    // fait planter le démarrage : le masque est donc construit sur les permissions accordées.
    private void seMettreAuPremierPlan() {
        int type = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
        }
        ServiceCompat.startForeground(this, NOTIFICATION, construireLaNotification(), type);
    }

    private void rafraichirLaNotification() {
        NotificationManager gestionnaire = getSystemService(NotificationManager.class);
        if (gestionnaire == null || !demarree) return;
        gestionnaire.notify(NOTIFICATION, construireLaNotification());
    }

    private Notification construireLaNotification() {
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("km", Phrases.formaterDistance(mesure.distanceM()));
        valeurs.put("temps", Phrases.formaterChrono(dureeS()));
        valeurs.put("allure", phrases.affichageAllure(mesure.allureCouranteKmh()));
        valeurs.put("unite", phrases.t("uniteAllure"));
        String resume = phrases.t("notification", valeurs);

        Intent ouvrir = new Intent(this, MainActivity.class);
        ouvrir.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        NotificationCompat.Builder constructeur = new NotificationCompat.Builder(this, CANAL)
            .setContentTitle(titre)
            .setContentText(resume.isEmpty() ? titre : resume)
            .setSmallIcon(R.drawable.ic_course)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    ouvrir,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                )
            );

        constructeur.addAction(
            0,
            phrases.t(enPause ? "resume" : "pause"),
            intentionDeService(ACTION_BASCULER_PAUSE, 1)
        );
        constructeur.addAction(0, phrases.t("listen"), intentionDeService(ACTION_ECOUTER, 2));
        return constructeur.build();
    }

    private PendingIntent intentionDeService(String action, int code) {
        Intent intention = new Intent(this, CourseService.class);
        intention.setAction(action);
        return PendingIntent.getService(
            this,
            code,
            intention,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void tenirLaVeille() {
        if (veille != null) return;
        PowerManager gestionnaire = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (gestionnaire == null) return;
        veille = gestionnaire.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chiron:course");
        veille.setReferenceCounted(false);
        veille.acquire(DUREE_VEILLE_MS);
    }

    private void relacherLaVeille() {
        if (veille == null) return;
        if (veille.isHeld()) veille.release();
        veille = null;
    }

    private JSONObject lireJson(String brut) {
        if (brut == null) return new JSONObject();
        try {
            return new JSONObject(brut);
        } catch (JSONException erreur) {
            return new JSONObject();
        }
    }

    private final class EcouteurDeVoix implements Ecoute.Ecouteur {

        @Override
        public void transcription(String texte, boolean definitif) {
            JSONObject donnees = new JSONObject();
            try {
                donnees.put("texte", texte);
                donnees.put("definitif", definitif);
            } catch (JSONException ignore) {}
            PontCourse.publier("commande", donnees);
        }

        @Override
        public void echec(String raison) {
            JSONObject donnees = new JSONObject();
            try {
                donnees.put("raison", raison);
            } catch (JSONException ignore) {}
            PontCourse.publier("echecEcoute", donnees);
            // WHY: « Répète » n'a de sens que si le moteur a écouté sans comprendre. Le dire
            // quand la permission manque ou qu'aucun moteur n'est installé enverrait l'athlète
            // répéter dans le vide pour le reste de la sortie.
            if (raison != null && raison.startsWith("erreur-")) {
                dire(phrases.t("notUnderstood"), true);
            }
            publierEtat();
        }
    }
}
