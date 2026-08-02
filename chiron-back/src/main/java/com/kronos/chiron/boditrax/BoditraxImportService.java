package com.kronos.chiron.boditrax;

import com.kronos.chiron.boditrax.BoditraxCsvParser.ParsedBoditrax;
import com.kronos.chiron.utilisateur.model.Sexe;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.visbody.VisbodyImportService;
import com.kronos.chiron.visbody.VisbodyImportService.ImportResult;
import com.kronos.chiron.visbody.VisbodyImportService.Outcome;
import com.kronos.chiron.visbody.VisbodyReport;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Importe un export CSV Boditrax pour un utilisateur : enregistre un
 * {@link com.kronos.chiron.visbody.BodyCompositionRecord} par scan (réutilise la
 * persistance idempotente de {@link VisbodyImportService}) et complète le profil
 * (taille / sexe / date de naissance) s'il est vide.
 */
@Service
@RequiredArgsConstructor
public class BoditraxImportService {

    private static final Logger log = LoggerFactory.getLogger(BoditraxImportService.class);
    private static final String SOURCE = "BODITRAX_CSV";

    private final BoditraxCsvParser parser;
    private final VisbodyImportService visbodyImportService;
    private final UtilisateurRepository utilisateurRepo;

    @Transactional
    public ImportResult importCsv(byte[] csv, Utilisateur user) {
        ParsedBoditrax data;
        try {
            data = parser.parse(csv);
        } catch (RuntimeException e) {
            log.warn("Boditrax : CSV illisible", e);
            return new ImportResult(Outcome.INVALID, "CSV Boditrax illisible : " + e.getMessage());
        }

        List<VisbodyReport> scans = data.scans();
        if (scans.isEmpty()) {
            return new ImportResult(Outcome.INVALID, "Aucun scan exploitable dans le CSV.");
        }
        scans.sort(Comparator.comparing(VisbodyReport::getMesureLe));

        // Complète le profil seulement s'il est vide (non destructif) : utile pour
        // personnaliser les bornes des jauges de composition.
        boolean profileChanged = false;
        if (user.getTailleCm() == null && data.tailleCm() != null) {
            user.setTailleCm(data.tailleCm());
            profileChanged = true;
        }
        if (user.getSexe() == null) {
            Sexe sexe = mapSexe(data.gender());
            if (sexe != null) {
                user.setSexe(sexe);
                profileChanged = true;
            }
        }
        if (user.getDateNaissance() == null && data.dateNaissance() != null) {
            user.setDateNaissance(data.dateNaissance());
            profileChanged = true;
        }
        if (profileChanged) utilisateurRepo.save(user);

        int imported = 0;
        int duplicates = 0;
        for (VisbodyReport report : scans) {
            ImportResult res = visbodyImportService.persist(report, user, SOURCE);
            switch (res.outcome()) {
                case IMPORTED -> imported++;
                case DUPLICATE -> duplicates++;
                default -> { /* ignoré */ }
            }
        }

        if (imported == 0) {
            return new ImportResult(Outcome.DUPLICATE,
                    "Aucun nouveau scan : " + duplicates + " déjà présent(s).");
        }
        String detail = imported + " scan(s) importé(s)"
                + (duplicates > 0 ? ", " + duplicates + " déjà présent(s)" : "") + ".";
        return new ImportResult(Outcome.IMPORTED, detail);
    }

    private Sexe mapSexe(String gender) {
        if (gender == null) return null;
        String g = gender.trim();
        if (g.equalsIgnoreCase("Male") || g.equalsIgnoreCase("Homme")) return Sexe.HOMME;
        if (g.equalsIgnoreCase("Female") || g.equalsIgnoreCase("Femme")) return Sexe.FEMME;
        return g.isBlank() ? null : Sexe.AUTRE;
    }
}
