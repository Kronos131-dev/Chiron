package com.kronos.chiron.course.service.impl;

import com.kronos.chiron.course.dto.CourseMesuresDto;
import com.kronos.chiron.course.dto.CoursePointDto;
import com.kronos.chiron.course.dto.CourseSplitDto;
import com.kronos.chiron.course.service.CourseGeometrieService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CourseGeometrieServiceImpl implements CourseGeometrieService {

    private static final double RAYON_TERRE_M = 6371008.8;
    private static final double KM_EN_METRES = 1000.0;
    private static final double SECONDES_PAR_HEURE = 3600.0;
    private static final double MS_PAR_SECONDE = 1000.0;

    // WHY: l'altitude GPS oscille de plusieurs mètres à l'arrêt. Sommer chaque écart positif
    // ferait grimper un parcours plat de centaines de mètres ; on n'ouvre un gain qu'au-delà
    // de ce seuil, en repartant du point bas dès que le terrain redescend.
    private static final double SEUIL_BRUIT_ALTITUDE_M = 2.0;

    @Override
    public CourseMesuresDto mesurer(List<CoursePointDto> points) {
        if (points == null || points.size() < 2) {
            return new CourseMesuresDto(0.0, 0, 0.0, List.of());
        }

        int dureeS = dureeEnSecondes(points);
        List<CourseSplitDto> splits = new ArrayList<>();
        double cumulM = 0.0;
        long debutKmMs = points.get(0).t();
        int prochainKm = 1;

        for (int i = 1; i < points.size(); i++) {
            CoursePointDto precedent = points.get(i - 1);
            CoursePointDto courant = points.get(i);
            double segmentM = distanceHaversineM(precedent, courant);
            if (segmentM <= 0) continue;

            double debutSegmentM = cumulM;
            cumulM += segmentM;

            while (cumulM >= prochainKm * KM_EN_METRES) {
                double cibleM = prochainKm * KM_EN_METRES;
                double fraction = (cibleM - debutSegmentM) / segmentM;
                long instantMs = precedent.t() + Math.round(fraction * (courant.t() - precedent.t()));
                int splitS = (int) Math.round((instantMs - debutKmMs) / MS_PAR_SECONDE);
                splits.add(new CourseSplitDto(prochainKm, splitS, allureKmh(KM_EN_METRES, splitS)));
                debutKmMs = instantMs;
                prochainKm++;
            }
        }

        return new CourseMesuresDto(cumulM, dureeS, denivelePositifM(points), List.copyOf(splits));
    }

    @Override
    public double allureKmh(double distanceM, int dureeS) {
        if (dureeS <= 0 || distanceM <= 0) return 0.0;
        return (distanceM / KM_EN_METRES) / (dureeS / SECONDES_PAR_HEURE);
    }

    private int dureeEnSecondes(List<CoursePointDto> points) {
        long debutMs = points.get(0).t();
        long finMs = points.get(points.size() - 1).t();
        return (int) Math.max(0, Math.round((finMs - debutMs) / MS_PAR_SECONDE));
    }

    private double distanceHaversineM(CoursePointDto a, CoursePointDto b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * RAYON_TERRE_M * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    private double denivelePositifM(List<CoursePointDto> points) {
        Double reference = null;
        double gain = 0.0;
        for (CoursePointDto point : points) {
            Double altitude = point.alt();
            if (altitude == null) continue;
            if (reference == null) {
                reference = altitude;
                continue;
            }
            double ecart = altitude - reference;
            if (ecart >= SEUIL_BRUIT_ALTITUDE_M) {
                gain += ecart;
                reference = altitude;
            } else if (ecart < 0) {
                reference = altitude;
            }
        }
        return gain;
    }
}
