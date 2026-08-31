package com.kronos.chiron.course;

import org.json.JSONObject;

public final class PontCourse {

    public interface Ecouteur {
        void evenement(String nom, JSONObject donnees);
    }

    private static Ecouteur ecouteur;
    private static CourseService service;

    private PontCourse() {}

    public static void brancher(Ecouteur nouvel) {
        ecouteur = nouvel;
    }

    public static void debrancher() {
        ecouteur = null;
    }

    static void publier(String nom, JSONObject donnees) {
        Ecouteur destinataire = ecouteur;
        if (destinataire != null) destinataire.evenement(nom, donnees);
    }

    static void enregistrer(CourseService nouveau) {
        service = nouveau;
    }

    static void oublier(CourseService ancien) {
        if (service == ancien) service = null;
    }

    public static CourseService service() {
        return service;
    }

    public static void configurerApiCommandes(String baseUrl, String token) {
        if (service != null) {
            service.configurerApiCommandes(baseUrl, token);
        }
    }
}
