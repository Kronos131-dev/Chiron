package com.kronos.chiron.repository;

import com.kronos.chiron.entity.ExerciceDefinition;
import com.kronos.chiron.entity.MuscleGroup;
import com.kronos.chiron.entity.NiveauDifficulte;
import com.kronos.chiron.entity.TypeEquipement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExerciceDefinitionRepository extends JpaRepository<ExerciceDefinition, Long> {

    Optional<ExerciceDefinition> findByExternalId(String externalId);

    @Query(value = "SELECT image0 FROM exercice_definition WHERE id = :id", nativeQuery = true)
    byte[] findImage0ById(@Param("id") Long id);

    @Query(value = "SELECT image1 FROM exercice_definition WHERE id = :id", nativeQuery = true)
    byte[] findImage1ById(@Param("id") Long id);

    // :q est passé déjà formaté en "%pattern%" (ou null) par le service.
    // COALESCE(:q,'')='' remplace :q IS NULL pour éviter l'inférence de type bytea de Hibernate 6 + PostgreSQL
    // sur les paramètres nommés utilisés dans des expressions CONCAT.
    @Query("""
            SELECT e FROM ExerciceDefinition e
            WHERE (COALESCE(:q, '') = '' OR LOWER(COALESCE(e.nomFr, '')) LIKE :q
                                         OR LOWER(e.nomEn) LIKE :q)
              AND (:muscle IS NULL OR e.musclePrincipal = :muscle)
              AND (:equipement IS NULL OR e.typeEquipement = :equipement)
              AND (:difficulte IS NULL OR e.difficulte = :difficulte)
            ORDER BY
              CASE WHEN LOWER(COALESCE(e.nomFr, '')) LIKE :qPrefix THEN 0 ELSE 1 END,
              e.usageCount DESC,
              COALESCE(e.nomFr, e.nomEn) ASC
            """)
    List<ExerciceDefinition> search(
            @Param("q") String q,
            @Param("qPrefix") String qPrefix,
            @Param("muscle") MuscleGroup muscle,
            @Param("equipement") TypeEquipement equipement,
            @Param("difficulte") NiveauDifficulte difficulte
    );

    @Modifying
    @Query("UPDATE ExerciceDefinition e SET e.usageCount = e.usageCount + 1 WHERE e.id IN :ids")
    void incrementUsageCount(@Param("ids") Collection<Long> ids);
}
