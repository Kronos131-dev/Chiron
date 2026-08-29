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

        List<CourseSplitDto> splits = new ArrayList<>();
        double cumulM = 0.0;
        long pauseCumuleeMs = 0;
        long debutKmMs = 0;
        int prochainKm = 1;

        for (int i = 1; i < points.size(); i++) {
            CoursePointDto precedent = points.get(i - 1);
            CoursePointDto courant = points.get(i);

            // WHY: le premier point après une reprise est marqué d'une coupure par le tracker.
            // Le segment qui l'atteint n'a pas été couru — l'athlète a pu marcher, monter dans
            // une voiture ou simplement rester à l'arrêt — et ni sa longueur ni sa durée
            // n'appartiennent à la sortie.
            if (courant.ouvreUneReprise()) {
                pauseCumuleeMs += courant.t() - precedent.t();
                continue;
            }

            double segmentM = distanceHaversineM(precedent, courant);
            if (segmentM <= 0) continue;

            double debutSegmentM = cumulM;
            cumulM += segmentM;

            long debutSegmentMs = tempsDeCourseMs(precedent, points.get(0), pauseCumuleeMs);
            long finSegmentMs = tempsDeCourseMs(courant, points.get(0), pauseCumuleeMs);

            while (cumulM >= prochainKm * KM_EN_METRES) {
                double cibleM = prochainKm * KM_EN_METRES;
                double fraction = (cibleM - debutSegmentM) / segmentM;
                long instantMs = debutSegmentMs + Math.round(fraction * (finSegmentMs - debutSegmentMs));
                int splitS = (int) Math.round((instantMs - debutKmMs) / MS_PAR_SECONDE);
                splits.add(new CourseSplitDto(prochainKm, splitS, allureKmh(KM_EN_METRES, splitS)));
                debutKmMs = instantMs;
                prochainKm++;
            }
        }

        int dureeS = dureeEnSecondes(points, pauseCumuleeMs);
        return new CourseMesuresDto(cumulM, dureeS, denivelePositifM(points), List.copyOf(splits));
    }

    // WHY: l'objectif tombe au milieu d'un segment GPS, jamais sur un point. La meme
    // interpolation que les splits kilometriques est ce qui rend le temps annonce dans les
    // oreilles identique a celui que le journal conservera.
    @Override
    public Integer tempsALaDistanceS(List<CoursePointDto> points, double objectifM) {
        if (points == null || points.size() < 2 || objectifM <= 0) return null;

        double cumulM = 0.0;
        long pauseCumuleeMs = 0;

        for (int i = 1; i < points.size(); i++) {
            CoursePointDto precedent = points.get(i - 1);
            CoursePointDto courant = points.get(i);

            if (courant.ouvreUneReprise()) {
                pauseCumuleeMs += courant.t() - precedent.t();
                continue;
            }

            double segmentM = distanceHaversineM(precedent, courant);
            if (segmentM <= 0) continue;

            double debutSegmentM = cumulM;
            cumulM += segmentM;
            if (cumulM < objectifM) continue;

            long debutSegmentMs = tempsDeCourseMs(precedent, points.get(0), pauseCumuleeMs);
            long finSegmentMs = tempsDeCourseMs(courant, points.get(0), pauseCumuleeMs);
            double fraction = (objectifM - debutSegmentM) / segmentM;
            long instantMs = debutSegmentMs + Math.round(fraction * (finSegmentMs - debutSegmentMs));
            return (int) Math.max(0, Math.round(instantMs / MS_PAR_SECONDE));
        }
        return null;
    }

    @Override
    public double allureKmh(double distanceM, int dureeS) {
        if (dureeS <= 0 || distanceM <= 0) return 0.0;
        return (distanceM / KM_EN_METRES) / (dureeS / SECONDES_PAR_HEURE);
    }

    private long tempsDeCourseMs(CoursePointDto point, CoursePointDto depart, long pauseCumuleeMs) {
        return point.t() - depart.t() - pauseCumuleeMs;
    }

    private int dureeEnSecondes(List<CoursePointDto> points, long pauseCumuleeMs) {
        long debutMs = points.get(0).t();
        long finMs = points.get(points.size() - 1).t();
        return (int) Math.max(0, Math.round((finMs - debutMs - pauseCumuleeMs) / MS_PAR_SECONDE));
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
