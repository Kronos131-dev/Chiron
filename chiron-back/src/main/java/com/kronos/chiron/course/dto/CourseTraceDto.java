package com.kronos.chiron.course.dto;

import java.util.List;

public record CourseTraceDto(
        Long id,
        double distanceM,
        int dureeS,
        double allureMoyenneKmh,
        double denivelePositifM,
        List<CourseSplitDto> splits,
        List<CoursePointDto> points) {
}
