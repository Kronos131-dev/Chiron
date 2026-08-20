package com.kronos.chiron.sante.model;

import com.kronos.chiron.sante.dto.SeuilsCardiaquesDto;

import java.util.Locale;

public enum ZoneCardiaque {
    HORS_ZONE(0), BRULE_GRAISSE(1), CARDIO(2), PIC(3);

    private final int poidsTrimp;

    ZoneCardiaque(int poidsTrimp) {
        this.poidsTrimp = poidsTrimp;
    }

    public int poidsTrimp() {
        return poidsTrimp;
    }

    public static ZoneCardiaque fromGoogle(String raw) {
        if (raw == null) return HORS_ZONE;
        String s = raw.toUpperCase(Locale.ROOT);
        if (s.contains("PEAK")) return PIC;
        if (s.contains("VIGOROUS") || s.contains("CARDIO")) return CARDIO;
        if (s.contains("MODERATE") || s.contains("FAT_BURN") || s.contains("FATBURN")) return BRULE_GRAISSE;
        return HORS_ZONE;
    }

    // WHY: bornes calculées par SeuilsCardiaquesService (réserve de Karvonen) — l'API
    // Google ne fournit jamais de seuil bpm, seulement des libellés de zone déjà agrégés.
    public static ZoneCardiaque fromBpm(int bpm, SeuilsCardiaquesDto seuils) {
        if (bpm >= seuils.maximum()) return PIC;
        if (bpm >= seuils.intense()) return CARDIO;
        if (bpm >= seuils.modere()) return BRULE_GRAISSE;
        return HORS_ZONE;
    }

    public static Double chargeCardio(Integer minutesBruleuse, Integer minutesCardio, Integer minutesPic) {
        if (minutesBruleuse == null && minutesCardio == null && minutesPic == null) {
            return null;
        }
        double bruleuse = minutesBruleuse != null ? minutesBruleuse : 0;
        double cardio = minutesCardio != null ? minutesCardio : 0;
        double pic = minutesPic != null ? minutesPic : 0;
        return bruleuse * BRULE_GRAISSE.poidsTrimp() + cardio * CARDIO.poidsTrimp() + pic * PIC.poidsTrimp();
    }
}
