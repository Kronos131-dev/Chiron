package com.kronos.chiron.course;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

public final class CommandeInterpreter {

    private static final long TIMEOUT_MS = 3000;
    private final Handler principal = new Handler(Looper.getMainLooper());
    private static final int TAMPON = 8192;
    private String baseUrl = null;
    private String token = null;

    public void configurerUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void configurerToken(String token) {
        this.token = token;
    }

    public interface Reponse {
        void interpretee(Commandes.Commande commande);
    }

    // WHY: l'appel bloquait le fil principal jusqu'a trois secondes, a chaque phrase entendue.
    // Les rappels du moteur de reconnaissance arrivent sur ce fil : pendant l'attente, ni la
    // voix ni la relance de l'ecoute ne pouvaient partir, et l'ordre semblait ignore avant de
    // sortir trois secondes plus tard — quand l'athlete avait deja renonce. L'appel part donc
    // sur son propre fil et rend sa reponse quand il l'a.
    public void interpreterEnLigne(String transcript, String langue, Reponse reponse) {
        if (baseUrl == null || token == null) {
            reponse.interpretee(null);
            return;
        }
        Thread fil = new Thread(() -> {
            Commandes.Commande trouvee = null;
            try {
                trouvee = appelApi(transcript, langue);
            } catch (Exception ignore) {}
            final Commandes.Commande resultat = trouvee;
            principal.post(() -> reponse.interpretee(resultat));
        });
        fil.setDaemon(true);
        fil.start();
    }

    private Commandes.Commande appelApi(String transcript, String langue)
        throws IOException, JSONException {
        URL url = new URL(baseUrl + "/api/courses/interpret-command?transcript=" +
                Uri.encode(transcript) + "&language=" + Uri.encode(langue));

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout((int) TIMEOUT_MS);
            conn.setReadTimeout((int) TIMEOUT_MS);

            if (conn.getResponseCode() != 200) {
                return null;
            }

            String reponse = lireReponse(conn);
            JSONObject json = new JSONObject(reponse);
            String nom = json.optString("nom", null);
            if (nom == null || nom.isEmpty()) {
                return null;
            }

            Double cible = null;
            if (json.has("cibleMinParKm") && !json.isNull("cibleMinParKm")) {
                cible = json.optDouble("cibleMinParKm");
            }

            return new Commandes.Commande(nom, cible);
        } finally {
            conn.disconnect();
        }
    }

    // WHY: InputStream.readAllBytes n'existe sur Android qu'a partir de l'API 33, et le projet
    // descend a 24. Sur un telephone plus ancien l'appel levait NoSuchMethodError — une Error,
    // que le catch(Exception) du fil ne rattrape pas : le verrou n'etait jamais notifie et
    // chaque commande vocale attendait les trois secondes du delai de garde avant de retomber
    // sur l'interpretation locale.
    private String lireReponse(HttpURLConnection conn) throws IOException {
        try (InputStream flux = conn.getInputStream()) {
            ByteArrayOutputStream accumule = new ByteArrayOutputStream();
            byte[] tampon = new byte[TAMPON];
            int lus;
            while ((lus = flux.read(tampon)) != -1) accumule.write(tampon, 0, lus);
            return accumule.toString(StandardCharsets.UTF_8.name());
        }
    }

}
