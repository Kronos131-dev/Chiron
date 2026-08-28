package com.kronos.chiron.course.persistence;

import com.kronos.chiron.course.model.CourseTrace;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseTraceRepository extends JpaRepository<CourseTrace, Long> {

    Optional<CourseTrace> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);

    boolean existsByIdAndUtilisateur(Long id, Utilisateur utilisateur);
}
