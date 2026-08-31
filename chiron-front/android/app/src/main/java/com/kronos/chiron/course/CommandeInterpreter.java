package com.kronos.chiron.course;

import android.net.Uri;
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
    private static final int TAMPON = 8192;
    private String baseUrl = null;
    private String token = null;

    public void configurerUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void configurerToken(String token) {
        this.token = token;
    }

    public Commandes.Commande interpreter(String transcript, String langue) {
        if (baseUrl == null || token == null) {
            return interpreterLocalement(transcript);
        }

        try {
            Commandes.Commande commande = appelApiAvecTimeout(transcript, langue);
            if (commande != null) return commande;
        } catch (Exception ignore) {}

        return interpreterLocalement(transcript);
    }

    private Commandes.Commande appelApiAvecTimeout(String transcript, String langue) throws Exception {
        final Commandes.Commande[] resultat = {null};
        final Exception[] erreur = {null};
        final Object verrou = new Object();

        Thread thread = new Thread(() -> {
            try {
                resultat[0] = appelApi(transcript, langue);
            } catch (Exception e) {
                erreur[0] = e;
            }
            synchronized (verrou) {
                verrou.notifyAll();
            }
        });
        thread.setDaemon(true);
        thread.start();

        synchronized (verrou) {
            try {
                verrou.wait(TIMEOUT_MS);
            } catch (InterruptedException ignore) {}
        }

        if (erreur[0] != null) throw erreur[0];
        return resultat[0];
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

    private Commandes.Commande interpreterLocalement(String transcript) {
        return Commandes.interpreter(transcript);
    }
}
