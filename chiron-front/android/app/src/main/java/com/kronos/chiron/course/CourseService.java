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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.kronos.chiron.MainActivity;
import com.kronos.chiron.R;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private static final long FENETRE_COMMANDE_MS = 8000;
    private static final long SILENCE_ENTRE_INCOMPREHENSIONS_MS = 20000;
    private static final int JOURNAL_MAX = 40;
    private static final int MOTS_MAX_ADRESSE = 4;
    private static final int LONGUEUR_MIN_REPONSE = 3;

    private final Handler boucle = new Handler(Looper.getMainLooper());
    private final Mesure mesure = new Mesure();
    private final Phrases phrases = new Phrases();
    private final CommandeInterpreter interpreterCommandes = new CommandeInterpreter();

    private Annonceur annonceur;
    private Ecoute ecoute;
    private Guetteur guetteur;
    private FusedLocationProviderClient gps;
    private LocationCallback rappelGps;
    private PowerManager.WakeLock veille;

    private boolean demarree = false;
    private boolean enPause = false;
    private boolean ouvrirUneCoupure = false;
    private long msAccumules = 0;
    private Long repriseA = null;
    private Double cibleMinParKm = null;
    private int paliersAnnonces = 0;
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
    private boolean motCleVoulu = true;
    private String motCleIndisponible = null;
    private volatile long fenetreJusquA = 0;
    private volatile long confirmationJusquA = 0;
    private boolean terminee = false;
    private long derniereIncomprehensionA = 0;
    private final Deque<String> journal = new ArrayDeque<>();
    private int transcriptsRecus = 0;
    private int ecoutesLancees = 0;
    private Integer derniereErreurMoteur = null;

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
        if (guetteur != null) guetteur.arreter();
        if (annonceur != null) annonceur.relacher();
        relacherLaVeille();
        super.onDestroy();
    }

    // WHY: l'arret ne detruit pas le service tout de suite — il se donne quelques secondes pour
    // que la voix finisse sa phrase. Une sortie relancee dans cet intervalle tombait sur la meme
    // instance, donc sur la distance, le chrono et les points de la precedente ; et l'arret
    // differe la tuait en pleine course. Le depart efface donc tout et annule ce qui est en
    // attente, plutot que de compter sur un objet neuf.
    private void repartirDeZero() {
        boucle.removeCallbacksAndMessages(null);
        couperGps();
        if (ecoute != null) ecoute.arreter();
        if (guetteur != null) guetteur.arreter();
        if (annonceur != null) annonceur.relacher();
        Archive.effacer(this);
        mesure.reinitialiser();
        enPause = false;
        ouvrirUneCoupure = false;
        msAccumules = 0;
        repriseA = null;
        paliersAnnonces = 0;
        ecartDepuis = null;
        derniereAnnonceEcart = 0;
        derniereReception = 0;
        precisionM = null;
        pointsPublies = 0;
        erreurGps = null;
        objectifAnnonce = false;
        demandeEcouteA = 0;
        fenetreJusquA = 0;
        confirmationJusquA = 0;
        motCleIndisponible = null;
        terminee = false;
        derniereIncomprehensionA = 0;
        journal.clear();
        transcriptsRecus = 0;
        ecoutesLancees = 0;
        derniereErreurMoteur = null;
    }

    private void demarrer(String configuration) {
        repartirDeZero();
        JSONObject config = lireJson(configuration);
        phrases.charger(config.optJSONObject("phrases"));
        phrases.fixerUnite(config.optString("uniteAllure", "minParKm"));
        titre = config.optString("titre", "Course");
        String langue = config.optString("langue", "fr");
        volumeVoix = config.optInt("volumeVoix", volumeVoix);
        objectifM = config.optDouble("objectifDistanceM", 0);
        mesure.fixerObjectif(objectifM);
        reglerIntervalle(config.optDouble("intervalleAnnonceM", Mesure.KM_EN_METRES));
        cibleMinParKm = null;
        if (config.has("cibleMinParKm") && !config.isNull("cibleMinParKm")) {
            cibleMinParKm = config.optDouble("cibleMinParKm");
        }

        seMettreAuPremierPlan();
        tenirLaVeille();

        annonceur = new Annonceur(this, langue);
        annonceur.fixerVolume(volumeVoix);
        annonceur.fixerTemoin(this::pendantLaParole);
        ecoute = new Ecoute(this, langue, new EcouteurDeVoix());
        motCleVoulu = config.optBoolean("motCle", true);
        guetteur = new Guetteur(this, langue, new EcouteurDuGuetteur());
        if (motCleVoulu) guetteur.demarrer();

        demarree = true;
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
            if (annonceur != null) annonceur.fixerVolume(volumeVoix);
        }
        if (config.has("objectifDistanceM")) {
            objectifM = config.optDouble("objectifDistanceM", 0);
            mesure.fixerObjectif(objectifM);
        }
        if (config.has("intervalleAnnonceM")) {
            reglerIntervalle(config.optDouble("intervalleAnnonceM", Mesure.KM_EN_METRES));
        }
        titre = config.optString("titre", titre);
        if (config.has("motCle")) reglerLeMotCle(config.optBoolean("motCle", true));
        rafraichirLaNotification();
    }

    public void configurerApiCommandes(String baseUrl, String token) {
        interpreterCommandes.configurerUrl(baseUrl);
        interpreterCommandes.configurerToken(token);
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
        if (guetteur != null) guetteur.arreter();
        if (annonceur != null) annonceur.interrompreEtParler(phrases.t("finished"));
        boucle.removeCallbacksAndMessages(null);
        archiver();
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

    public void essayerVoix(String texte, int volume) {
        volumeVoix = volume;
        if (annonceur == null) return;
        annonceur.fixerVolume(volume);
        annonceur.interrompreEtParler(texte);
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
        // WHY: le guetteur ecoute deja en permanence, et Android ne prete qu'un moteur de
        // reconnaissance a la fois — en ouvrir un second rend ERROR_RECOGNIZER_BUSY et tue
        // l'ecoute pour le reste de la sortie. Quand le guetteur veille, le bouton micro
        // n'allume donc rien : il ouvre la fenetre d'ordre de celui qui entend deja.
        if (guetteur != null && guetteur.actif()) {
            ouvrirLaFenetre();
            publierEtat();
            return;
        }
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
            fenetreJusquA = System.currentTimeMillis() + FENETRE_COMMANDE_MS;
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
        fenetreJusquA = System.currentTimeMillis() + FENETRE_COMMANDE_MS;
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
        annoncerLesPaliers();
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

    // WHY: seul le dernier palier franchi est annoncé. Après une reprise de signal plusieurs
    // peuvent tomber d'un tick au suivant, et les enchaîner couvrirait le palier en cours sans
    // rien apprendre.
    private void annoncerLesPaliers() {
        int franchis = mesure.paliersFranchis();
        if (franchis <= paliersAnnonces) return;
        paliersAnnonces = franchis;
        // WHY: c'est l'allure du palier qui vient d'être bouclé qui apprend quelque chose, pas
        // la moyenne depuis le départ — celle-ci se lisse et cesse de réagir au bout d'une
        // demi-heure, alors que l'athlète veut savoir s'il tient ou s'il s'écroule.
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("distance", phrases.distanceParlee(mesure.distanceDesPaliersM()));
        valeurs.put("temps", phrases.dureeParlee(dureeS()));
        valeurs.put("allure", phrases.allureParlee(mesure.allureDuDernierPalierKmh()));
        dire(phrases.t("km", valeurs), false);
    }

    // WHY: rebaser le compteur d'annonces sur ce que la mesure vient de recalculer evite qu'un
    // resserrement de l'intervalle en pleine course ne declenche d'un coup toutes les annonces
    // des paliers deja parcourus.
    private void reglerIntervalle(double metres) {
        mesure.fixerIntervalle(metres);
        paliersAnnonces = mesure.paliersFranchis();
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
            etat.put("paliers", mesure.paliersFranchis());
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
            etat.put("motCleActif", guetteur != null && guetteur.actif());
            etat.put(
                "motCleIndisponible",
                motCleIndisponible == null ? JSONObject.NULL : motCleIndisponible
            );
            etat.put("terminee", terminee);
            etat.put("transcriptsRecus", transcriptsRecus);
            etat.put("ecoutesLancees", ecoutesLancees);
            etat.put(
                "derniereErreurMoteur",
                derniereErreurMoteur == null ? JSONObject.NULL : derniereErreurMoteur
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

    private void pendantLaParole(boolean parle) {
        if (guetteur == null) return;
        if (parle) guetteur.suspendre();
        else guetteur.reprendre();
    }

    private void reglerLeMotCle(boolean voulu) {
        if (voulu == motCleVoulu || guetteur == null) return;
        motCleVoulu = voulu;
        if (voulu) {
            motCleIndisponible = null;
            guetteur.demarrer();
        } else {
            guetteur.arreter();
        }
        publierEtat();
    }

    // WHY: la fenetre ne s'ouvre qu'une fois « J'ecoute » prononce. La compter des la detection
    // ferait courir son delai pendant que Chiron parle encore, et l'athlete parlerait dans une
    // fenetre deja a moitie fermee.
    private void ouvrirLaFenetre() {
        if (annonceur == null) {
            fenetreJusquA = System.currentTimeMillis() + FENETRE_COMMANDE_MS;
            return;
        }
        annonceur.parlerPuis(
            phrases.t("listening"),
            () -> fenetreJusquA = System.currentTimeMillis() + FENETRE_COMMANDE_MS
        );
    }

    private boolean fenetreOuverte() {
        return System.currentTimeMillis() < fenetreJusquA;
    }

    private boolean attendUneConfirmation() {
        return System.currentTimeMillis() < confirmationJusquA;
    }

    // WHY: un guetteur permanent voit passer chaque bribe de conversation et chaque parole
    // chantee. Reveiller la WebView a chacune reviendrait a la tenir allumee toute la sortie :
    // les partiels ne franchissent la passerelle que pendant un echange, les phrases achevees
    // toujours — ce sont elles que l'ecran affiche pour dire ce que le moteur a vraiment entendu.
    private void recevoirTranscript(String texte, boolean definitif) {
        if (definitif || fenetreOuverte() || attendUneConfirmation()) {
            JSONObject donnees = new JSONObject();
            try {
                donnees.put("texte", texte);
                donnees.put("definitif", definitif);
            } catch (JSONException ignore) {}
            PontCourse.publier("commande", donnees);
        }
        if (!definitif || !demarree) return;

        if (attendUneConfirmation()) {
            repondreALaConfirmation(texte);
            return;
        }
        transcriptsRecus++;

        // WHY: le mot-cle n'est plus exige. L'athlete court seul, avec des ecouteurs, et le
        // vocabulaire fait dix mots : un declenchement involontaire coute une annonce de trop,
        // un ordre manque coute la fonction entiere. Le nom du coach est donc retire s'il est la,
        // et ignore s'il n'y est pas.
        boolean dansLaFenetre = fenetreOuverte();
        Commandes.Reveil reveil = Commandes.detecterMotCle(texte);
        boolean nomme = reveil != null;
        String ordre = nomme ? reveil.suite : Commandes.normaliser(texte);

        if (ordre.isEmpty()) {
            noter(texte, nomme ? "nom seul" : "vide");
            if (nomme && reveil.avecInterjection) ouvrirLaFenetre();
            return;
        }
        executerLeTexte(ordre, texte, nomme, dansLaFenetre);
    }

    // WHY: « nothing happens » etait le seul retour possible, quel que soit le maillon casse —
    // permission, moteur, modele, micro, transcript, interpretation. Le journal est ce qui rend
    // la difference lisible apres coup, et les compteurs disent si le moteur a seulement demarre.
    private void noter(String texte, String decision) {
        journal.addFirst(Phrases.formaterChrono(dureeS()) + " · " + texte + " → " + decision);
        while (journal.size() > JOURNAL_MAX) journal.removeLast();
    }

    public JSONArray journalJson() {
        JSONArray lignes = new JSONArray();
        for (String ligne : journal) lignes.put(ligne);
        return lignes;
    }

    // WHY: l'interpretation locale passe en premier. Elle est instantanee, hors ligne, et couvre
    // le vocabulaire de dix mots de la course ; l'IA n'est plus qu'un second recours pour une
    // tournure inhabituelle. La consulter d'abord coutait trois secondes a chaque phrase et
    // echouait la ou l'on court — loin de tout reseau.
    private void executerLeTexte(String ordre, String entendu, boolean nomme, boolean dansLaFenetre) {
        fenetreJusquA = 0;
        boolean adresse = nomme || dansLaFenetre || estCourt(ordre);

        Commandes.Commande locale = Commandes.interpreter(ordre);
        if (locale != null) {
            appliquerLOrdre(locale, entendu, nomme);
            return;
        }
        if (!adresse) {
            noter(entendu, "ignore");
            return;
        }
        interpreterCommandes.interpreterEnLigne(ordre, "fr", distante -> {
            if (distante != null) {
                appliquerLOrdre(distante, entendu, nomme);
                return;
            }
            noter(entendu, "incompris");
            repondreIncompris(entendu, true);
        });
    }

    private void appliquerLOrdre(Commandes.Commande commande, String entendu, boolean nomme) {
        // WHY: terminer est la seule commande qui clot la sortie. Elle garde le nom du coach
        // parce qu'elle est la seule qu'un mot mal entendu rendrait couteuse.
        if (commande.nom.equals("terminer") && !nomme) {
            noter(entendu, "terminer sans le nom");
            dire(phrases.t("finishNeedsName"), true);
            return;
        }
        noter(entendu, "commande " + commande.nom);
        executerCommande(commande);
    }

    // WHY: se taire sur ce qu'on n'a pas compris rendait la panne invisible — l'athlete ne
    // pouvait pas distinguer un moteur muet d'une phrase mal interpretee. Chiron repete donc ce
    // qu'il a cru entendre, dans l'oreille, en courant. Le silence de vingt secondes est ce qui
    // l'empeche de commenter chaque bribe de conversation captee.
    private void repondreIncompris(String entendu, boolean adresse) {
        if (!adresse || entendu.trim().length() < LONGUEUR_MIN_REPONSE) return;
        long maintenant = System.currentTimeMillis();
        if (maintenant - derniereIncomprehensionA < SILENCE_ENTRE_INCOMPREHENSIONS_MS) return;
        derniereIncomprehensionA = maintenant;
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("texte", entendu);
        dire(phrases.t("heard", valeurs), true);
    }

    // WHY: une phrase courte, pendant une sortie en solitaire, est presque toujours un ordre
    // rate. Une phrase longue est presque toujours une conversation qui passe.
    private static boolean estCourt(String ordre) {
        return ordre.split(" ").length <= MOTS_MAX_ADRESSE;
    }

    private void executerCommande(Commandes.Commande commande) {
        switch (commande.nom) {
            case "pause":
                if (!enPause) basculerPause();
                return;
            case "reprendre":
                if (enPause) basculerPause();
                return;
            case "cible":
                if (commande.cibleMinParKm != null) fixerCible(commande.cibleMinParKm);
                return;
            case "terminer":
                demanderLaFinDeCourse();
                return;
            default:
                executerAction(commande.nom);
        }
    }

    // WHY: une sortie ne se clot pas sur un mot mal entendu. La demande de confirmation coute
    // une seconde a l'arrivee, et elle est le seul rempart entre un bruit de rue et une course
    // perdue au trente-cinquieme kilometre.
    private void demanderLaFinDeCourse() {
        if (annonceur == null) {
            terminerLaCourse();
            return;
        }
        annonceur.parlerPuis(
            phrases.t("confirmFinish"),
            () -> confirmationJusquA = System.currentTimeMillis() + FENETRE_COMMANDE_MS
        );
        publierEtat();
    }

    private void repondreALaConfirmation(String texte) {
        confirmationJusquA = 0;
        if (Commandes.estUneConfirmation(texte)) {
            terminerLaCourse();
            return;
        }
        dire(phrases.t("finishCancelled"), true);
    }

    private void terminerLaCourse() {
        terminee = true;
        arreter();
    }

    // WHY: l'etat final et les points partent sur le disque parce que la page peut n'etre
    // rouverte que bien apres la mort du service. C'est la seule facon pour le journal de
    // recuperer une sortie close a la voix ou depuis le bouton de la notification.
    private void archiver() {
        JSONObject contenu = etat();
        try {
            contenu.put("terminee", true);
            contenu.put("points", pointsJson());
        } catch (JSONException ignore) {}
        Archive.ecrire(this, contenu);
    }

    private final class EcouteurDuGuetteur implements Guetteur.Ecouteur {

        @Override
        public void entendu(String texte, boolean definitif) {
            recevoirTranscript(texte, definitif);
        }

        @Override
        public void indisponible(String raison) {
            motCleIndisponible = raison;
            noter("", "guetteur indisponible : " + raison);
            publierEtat();
        }

        @Override
        public void ecouteLancee() {
            ecoutesLancees++;
        }

        @Override
        public void erreurMoteur(int code) {
            derniereErreurMoteur = code;
        }
    }

    private final class EcouteurDeVoix implements Ecoute.Ecouteur {

        @Override
        public void transcription(String texte, boolean definitif) {
            recevoirTranscript(texte, definitif);
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
