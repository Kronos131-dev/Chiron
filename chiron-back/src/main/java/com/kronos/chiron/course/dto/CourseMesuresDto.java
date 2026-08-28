package com.kronos.chiron.course.dto;

import java.util.List;

public record CourseMesuresDto(
        double distanceM,
        int dureeS,
        double denivelePositifM,
        List<CourseSplitDto> splits) {
}
