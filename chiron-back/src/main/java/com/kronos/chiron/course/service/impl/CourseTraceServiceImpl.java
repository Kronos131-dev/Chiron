package com.kronos.chiron.course.service.impl;

import static com.kronos.chiron.core.exceptions.ErrorFactory.badRequest;
import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.course.dto.CourseMesuresDto;
import com.kronos.chiron.course.dto.CoursePointDto;
import com.kronos.chiron.course.dto.CourseSplitDto;
import com.kronos.chiron.course.dto.CourseTraceDto;
import com.kronos.chiron.course.dto.CourseTraceRequestDto;
import com.kronos.chiron.course.model.CourseTrace;
import com.kronos.chiron.course.persistence.CourseTraceRepository;
import com.kronos.chiron.course.service.CourseGeometrieService;
import com.kronos.chiron.course.service.CourseTraceService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseTraceServiceImpl implements CourseTraceService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int POINTS_MAX = 50000;
    private static final int POINTS_MIN = 2;

    private final CourseTraceRepository courseTraceRepository;
    private final CourseGeometrieService courseGeometrieService;
    private final Clock clock;

    @Override
    @Transactional
    public CourseTraceDto enregistrer(Utilisateur utilisateur, CourseTraceRequestDto requete) {
        List<CoursePointDto> points = requete == null ? null : requete.points();
        if (points == null || points.size() < POINTS_MIN) {
            throw badRequest("Une trace de course demande au moins " + POINTS_MIN + " points.");
        }
        if (points.size() > POINTS_MAX) {
            throw badRequest("Une trace de course ne peut pas dépasser " + POINTS_MAX + " points.");
        }

        CourseMesuresDto mesures = courseGeometrieService.mesurer(points);
        Double objectifM = requete.objectifDistanceM();
        Integer objectifDureeS = objectifM == null
                ? null
                : courseGeometrieService.tempsALaDistanceS(points, objectifM);

        CourseTrace trace = courseTraceRepository.save(CourseTrace.builder()
                .utilisateur(utilisateur)
                .points(JSON.writeValueAsString(points))
                .nbPoints(points.size())
                .distanceM(mesures.distanceM())
                .dureeS(mesures.dureeS())
                .denivelePositifM(mesures.denivelePositifM())
                .splits(JSON.writeValueAsString(mesures.splits()))
                .objectifDistanceM(objectifM)
                .objectifDureeS(objectifDureeS)
                .createdAt(LocalDateTime.now(clock))
                .build());

        return versDto(trace, points, mesures.splits());
    }

    @Override
    @Transactional(readOnly = true)
    public CourseTraceDto lire(Utilisateur utilisateur, Long id) {
        CourseTrace trace = courseTraceRepository.findByIdAndUtilisateur(id, utilisateur)
                .orElseThrow(() -> notFound("trace de course", id));
        return versDto(trace, lirePoints(trace), lireSplits(trace));
    }

    private CourseTraceDto versDto(CourseTrace trace, List<CoursePointDto> points, List<CourseSplitDto> splits) {
        return new CourseTraceDto(
                trace.getId(),
                trace.getDistanceM(),
                trace.getDureeS(),
                courseGeometrieService.allureKmh(trace.getDistanceM(), trace.getDureeS()),
                trace.getDenivelePositifM(),
                trace.getObjectifDistanceM(),
                trace.getObjectifDureeS(),
                splits,
                points);
    }

    private List<CoursePointDto> lirePoints(CourseTrace trace) {
        return JSON.readValue(trace.getPoints(), new TypeReference<List<CoursePointDto>>() {
        });
    }

    private List<CourseSplitDto> lireSplits(CourseTrace trace) {
        if (trace.getSplits() == null || trace.getSplits().isBlank()) return List.of();
        return JSON.readValue(trace.getSplits(), new TypeReference<List<CourseSplitDto>>() {
        });
    }
}
