package com.kronos.chiron.exportation.persistence;

import com.kronos.chiron.exportation.model.ExportProgramme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExportProgrammeRepository extends JpaRepository<ExportProgramme, Long> {

    List<ExportProgramme> findByUtilisateurIdOrderByCreeADesc(Long utilisateurId);

    Optional<ExportProgramme> findByIdAndUtilisateurId(Long id, Long utilisateurId);
}
