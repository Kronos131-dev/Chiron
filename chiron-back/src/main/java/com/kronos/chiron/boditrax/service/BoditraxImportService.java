package com.kronos.chiron.boditrax.service;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.visbody.service.VisbodyImportService.ImportResult;

public interface BoditraxImportService {

    ImportResult importCsv(byte[] csv, Utilisateur user);
}
