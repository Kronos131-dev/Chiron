package com.kronos.chiron.course;

import android.net.Uri;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

public final class CommandeInterpreter {

    private static final long TIMEOUT_MS = 3000;
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

    private Commandes.Commande appelApiAvecTimeout(String transcript, String langue) throws IOException {
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

        if (erreur[0] != null) throw (IOException) erreur[0];
        return resultat[0];
    }

    private Commandes.Commande appelApi(String transcript, String langue) throws IOException {
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

    private String lireReponse(HttpURLConnection conn) throws IOException {
        java.io.InputStream flux = conn.getInputStream();
        byte[] contenu = flux.readAllBytes();
        flux.close();
        return new String(contenu, StandardCharsets.UTF_8);
    }

    private Commandes.Commande interpreterLocalement(String transcript) {
        return Commandes.interpreter(transcript);
    }
}
