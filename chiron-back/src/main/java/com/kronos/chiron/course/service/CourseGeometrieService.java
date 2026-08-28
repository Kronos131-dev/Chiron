package com.kronos.chiron.course.service;

import com.kronos.chiron.course.dto.CourseMesuresDto;
import com.kronos.chiron.course.dto.CoursePointDto;

import java.util.List;

public interface CourseGeometrieService {

    CourseMesuresDto mesurer(List<CoursePointDto> points);

    double allureKmh(double distanceM, int dureeS);
}
