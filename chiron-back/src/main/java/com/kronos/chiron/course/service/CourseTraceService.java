package com.kronos.chiron.course.service;

import com.kronos.chiron.course.dto.CourseTraceDto;
import com.kronos.chiron.course.dto.CourseTraceRequestDto;
import com.kronos.chiron.utilisateur.model.Utilisateur;

public interface CourseTraceService {

    CourseTraceDto enregistrer(Utilisateur utilisateur, CourseTraceRequestDto requete);

    CourseTraceDto lire(Utilisateur utilisateur, Long id);
}
