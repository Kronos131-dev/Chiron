package com.kronos.chiron.noctua.service;

import com.kronos.chiron.noctua.model.NoctuaBriefing;
import com.kronos.chiron.noctua.model.NoctuaBriefingType;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import java.time.LocalDate;
import java.util.Optional;

public interface NoctuaBriefingService {

    boolean dejaProduit(Utilisateur user, String cleDeclencheur);

    Optional<NoctuaBriefing> genererSiNecessaire(Utilisateur user, NoctuaBriefingType type, LocalDate dateReference,
            String cleDeclencheur, String titre, String declencheurLisible, String commandeSysteme);
}
