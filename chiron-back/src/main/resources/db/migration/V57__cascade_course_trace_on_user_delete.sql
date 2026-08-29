-- V57 : la suppression d'un compte butait sur ses traces de course.
-- course_trace était la seule table possédée par un utilisateur dont la clé étrangère ne
-- cascadait pas : supprimer un athlète ayant couru dehors levait une violation d'intégrité,
-- rendue au client en 400 sans rien dire de la cause.
ALTER TABLE course_trace DROP CONSTRAINT fk_course_trace_utilisateur;
ALTER TABLE course_trace ADD CONSTRAINT fk_course_trace_utilisateur
    FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE CASCADE;

-- serie.course_trace_id est un simple pointeur nullable, sans association JPA. Le mettre à
-- NULL plutôt que de refuser la suppression enlève toute dépendance à l'ordre des DELETE
-- émis par Hibernate quand un compte s'en va.
ALTER TABLE serie DROP CONSTRAINT fk_serie_course_trace;
ALTER TABLE serie ADD CONSTRAINT fk_serie_course_trace
    FOREIGN KEY (course_trace_id) REFERENCES course_trace (id) ON DELETE SET NULL;
