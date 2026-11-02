package com.kronos.chiron.exportation.service.classeur;

import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.kronos.chiron.core.exceptions.ErrorFactory.serviceUnavailable;

@Component
public class ClasseurXlsx {

    public byte[] generer(DonneesExport donnees) {
        try {
            return EcritureXlsx.ecrire(new ClasseurBuilder().onglets(donnees));
        } catch (IOException e) {
            throw serviceUnavailable("Impossible de générer le fichier XLSX.", e);
        }
    }
}
