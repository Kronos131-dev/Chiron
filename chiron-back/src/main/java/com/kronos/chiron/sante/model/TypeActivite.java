package com.kronos.chiron.sante.model;

import java.util.Locale;

public enum TypeActivite {
    MUSCULATION, MARCHE, COURSE, VELO, FOOTBALL, SPORT_AUTRE;

    public static TypeActivite fromGoogle(String raw) {
        if (raw == null) return SPORT_AUTRE;
        String s = raw.toUpperCase(Locale.ROOT);
        if (s.contains("WALK")) return MARCHE;
        if (s.contains("RUN")) return COURSE;
        if (s.contains("BIK") || s.contains("CYCL")) return VELO;
        if (s.contains("SOCCER") || s.contains("FOOTBALL")) return FOOTBALL;
        if (s.contains("WEIGHT") || s.contains("STRENGTH") || s.contains("CROSSFIT")) return MUSCULATION;
        return SPORT_AUTRE;
    }
}
