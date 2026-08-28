package com.kronos.chiron.course.dto;

public record CoursePointDto(double lat, double lon, long t, Double alt, Boolean coupure) {

    public CoursePointDto(double lat, double lon, long t, Double alt) {
        this(lat, lon, t, alt, null);
    }

    public boolean ouvreUneReprise() {
        return Boolean.TRUE.equals(coupure);
    }
}
