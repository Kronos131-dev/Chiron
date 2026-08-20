package com.kronos.chiron.sante.service;

import com.kronos.chiron.sante.dto.SanteActiviteDetailDto;
import com.kronos.chiron.sante.dto.SanteActiviteDto;
import com.kronos.chiron.sante.dto.SanteCardioHebdoDto;
import com.kronos.chiron.sante.dto.SanteFcJourDto;
import com.kronos.chiron.sante.dto.SanteFcPointDto;
import com.kronos.chiron.sante.dto.SanteJourDto;
import com.kronos.chiron.sante.dto.SanteResumeDto;
import com.kronos.chiron.sante.dto.SanteSommeilDto;
import com.kronos.chiron.sante.dto.SanteSyncEtatDto;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SanteQueryService {

    SanteResumeDto getResume(Utilisateur utilisateur);

    List<SanteJourDto> getJours(Utilisateur utilisateur, int jours);

    List<SanteFcPointDto> getFrequenceCardiaqueJour(Utilisateur utilisateur, LocalDate date);

    List<SanteFcJourDto> getFrequenceCardiaquePlage(Utilisateur utilisateur, int jours);

    List<SanteSommeilDto> getSommeil(Utilisateur utilisateur, int jours);

    List<SanteCardioHebdoDto> getCardioHebdo(Utilisateur utilisateur, int semaines);

    List<SanteSyncEtatDto> getSyncEtat(Utilisateur utilisateur);

    List<SanteActiviteDto> getActivites(Utilisateur utilisateur, int jours, SourceActivite sourceFiltre);

    Optional<SanteActiviteDetailDto> getActiviteDetail(Utilisateur utilisateur, Long id);
}
